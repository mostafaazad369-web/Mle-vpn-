#!/usr/bin/env python3
# Copyright (C) mlmvpn — adapted for MLE VPN from:
# https://github.com/mlmvpn/mlmvpn_android/blob/main/scripts/repackage-psiphon-aar.py
# Licensed under GNU GPLv3; preserve attribution to mlmvpn.
"""Namespace-isolate Xray's gomobile runtime from the existing sing-box runtime.

The upstream Xray AAR contains go.Seq/go.Universe and libgojni.so. The sing-box
AAR also contains the Go mobile Java runtime. This script moves Xray's Java
runtime to pg.* and its JNI library to libpgjni.so.

ELF hash handling: some AndroidLibXrayLite builds have DT_HASH; newer ones
carry only DT_GNU_HASH. For GNU-only libraries, the script replaces the GNU
hash table in-place with a SysV hash table (which does not require dynsym
sorting) and changes the dynamic tag. It refuses to continue if the existing
GNU hash-table allocation is too small.
"""

import os
import re
import shutil
import struct
import sys
import tempfile
import zipfile
import io

OLD_LIB, NEW_LIB = "gojni", "pgjni"
CLASS_SUBS = [
    (b"go/Seq", b"pg/Seq"),
    (b"go/Universe", b"pg/Universe"),
    (b"go/error", b"pg/error"),
    (b"gojni", b"pgjni"),
]
SO_SUBS = [
    (b"Java_go_Seq", b"Java_pg_Seq"),
    (b"Java_go_Universe", b"Java_pg_Universe"),
    (b"go/Seq", b"pg/Seq"),
    (b"go/Universe", b"pg/Universe"),
]

DT_NULL, DT_HASH, DT_STRTAB, DT_SYMTAB, DT_SYMENT = 0, 4, 5, 6, 11
DT_GNU_HASH = 0x6FFFFEF5
SHT_DYNSYM = 11


def substitute(data, subs):
    count = 0
    for old, new in subs:
        if len(old) != len(new):
            raise ValueError(f"length-changing replacement: {old!r} -> {new!r}")
        count += data.count(old)
        data = data.replace(old, new)
    return data, count


def elf_hash(name):
    h = 0
    for byte in name:
        h = ((h << 4) + byte) & 0xffffffff
        g = h & 0xf0000000
        if g:
            h ^= g >> 24
        h &= ~g & 0xffffffff
    return h


class Elf:
    def __init__(self, data):
        self.data = bytearray(data)
        d = self.data
        if d[:4] != b"\x7fELF":
            raise ValueError("not an ELF shared library")
        self.is64 = d[4] == 2
        if d[5] != 1:
            raise ValueError("only little-endian Android ELF is supported")
        if self.is64:
            self.phoff = struct.unpack_from("<Q", d, 0x20)[0]
            self.phentsize, self.phnum = struct.unpack_from("<HH", d, 0x36)
            self.shoff = struct.unpack_from("<Q", d, 0x28)[0]
            self.shentsize, self.shnum = struct.unpack_from("<HH", d, 0x3a)
        else:
            self.phoff = struct.unpack_from("<I", d, 0x1c)[0]
            self.phentsize, self.phnum = struct.unpack_from("<HH", d, 0x2a)
            self.shoff = struct.unpack_from("<I", d, 0x20)[0]
            self.shentsize, self.shnum = struct.unpack_from("<HH", d, 0x2e)
        self.loads, self.dynoff = [], None
        for i in range(self.phnum):
            off = self.phoff + i * self.phentsize
            p_type = struct.unpack_from("<I", d, off)[0]
            if self.is64:
                p_offset, p_vaddr = struct.unpack_from("<QQ", d, off + 8)
                p_filesz = struct.unpack_from("<Q", d, off + 32)[0]
            else:
                p_offset, p_vaddr = struct.unpack_from("<II", d, off + 4)
                p_filesz = struct.unpack_from("<I", d, off + 16)[0]
            if p_type == 1:
                self.loads.append((p_vaddr, p_offset, p_filesz))
            elif p_type == 2:
                self.dynoff = p_offset
        if self.dynoff is None:
            raise ValueError("ELF has no PT_DYNAMIC segment")

    def v2o(self, addr):
        for vaddr, offset, size in self.loads:
            if vaddr <= addr < vaddr + size:
                return offset + addr - vaddr
        raise ValueError(f"virtual address {addr:#x} is outside PT_LOAD")

    def dynamic_entries(self):
        fmt, step = ("<qQ", 16) if self.is64 else ("<iI", 8)
        cursor = self.dynoff
        while True:
            tag, val = struct.unpack_from(fmt, self.data, cursor)
            yield cursor, tag, val
            if tag == DT_NULL:
                break
            cursor += step

    def dynamic(self):
        result = {}
        for _, tag, val in self.dynamic_entries():
            if tag == DT_NULL:
                break
            result.setdefault(tag, val)
        return result

    def dynsym_count(self):
        if not self.shoff or not self.shnum:
            return None
        for i in range(self.shnum):
            off = self.shoff + i * self.shentsize
            sh_type = struct.unpack_from("<I", self.data, off + 4)[0]
            if sh_type != SHT_DYNSYM:
                continue
            if self.is64:
                size = struct.unpack_from("<Q", self.data, off + 32)[0]
                entsize = struct.unpack_from("<Q", self.data, off + 56)[0]
            else:
                size = struct.unpack_from("<I", self.data, off + 20)[0]
                entsize = struct.unpack_from("<I", self.data, off + 36)[0]
            if entsize:
                return size // entsize
        return None


def has_soname(data):
    elf = Elf(data)
    return 14 in elf.dynamic()  # DT_SONAME


def symbol_name(data, symtab_off, strtab_off, syment, index):
    st_name = struct.unpack_from("<I", data, symtab_off + index * syment)[0]
    start = strtab_off + st_name
    end = data.index(b"\x00", start)
    return bytes(data[start:end])


def build_sysv_table(elf, hash_off, nchain, nbucket, symtab_off, strtab_off, syment):
    d = elf.data
    buckets = [0] * nbucket
    chains = [0] * nchain
    for index in range(nchain - 1, 0, -1):
        name = symbol_name(d, symtab_off, strtab_off, syment, index)
        if not name:
            continue
        slot = elf_hash(name) % nbucket
        chains[index] = buckets[slot]
        buckets[slot] = index
    struct.pack_into("<II", d, hash_off, nbucket, nchain)
    struct.pack_into(f"<{nbucket}I", d, hash_off + 8, *buckets)
    struct.pack_into(f"<{nchain}I", d, hash_off + 8 + nbucket * 4, *chains)


def convert_gnu_hash_to_sysv(elf, tags):
    d = elf.data
    gnu_addr = tags[DT_GNU_HASH]
    hash_off = elf.v2o(gnu_addr)
    gnu_buckets, symoffset, bloom_size, _ = struct.unpack_from("<IIII", d, hash_off)
    word_size = 8 if elf.is64 else 4
    buckets_off = hash_off + 16 + bloom_size * word_size
    chains_off = buckets_off + gnu_buckets * 4

    nchain = elf.dynsym_count()
    if nchain is None:
        last = symoffset - 1
        for i in range(gnu_buckets):
            index = struct.unpack_from("<I", d, buckets_off + i * 4)[0]
            if index == 0:
                continue
            while True:
                chain = struct.unpack_from("<I", d, chains_off + (index - symoffset) * 4)[0]
                last = max(last, index)
                if chain & 1:
                    break
                index += 1
        nchain = last + 1

    if nchain <= symoffset:
        raise ValueError("invalid GNU hash/dynsym bounds")
    gnu_size = 16 + bloom_size * word_size + gnu_buckets * 4 + (nchain - symoffset) * 4

    symtab_off = elf.v2o(tags[DT_SYMTAB])
    strtab_off = elf.v2o(tags[DT_STRTAB])
    syment = tags.get(DT_SYMENT, 24 if elf.is64 else 16)

    # Pick the largest practical bucket count that fits in the old GNU hash allocation.
    max_buckets = (gnu_size - 8 - nchain * 4) // 4
    if max_buckets < 1:
        raise ValueError(
            f"GNU hash allocation ({gnu_size} bytes) is too small for a SysV table "
            f"with {nchain} dynamic symbols"
        )
    nbucket = min(max(1, gnu_buckets), 257, max_buckets)
    build_sysv_table(elf, hash_off, nchain, nbucket, symtab_off, strtab_off, syment)

    fmt = "<qQ" if elf.is64 else "<iI"
    replaced = False
    for off, tag, value in elf.dynamic_entries():
        if tag == DT_GNU_HASH:
            struct.pack_into(fmt, d, off, DT_HASH, gnu_addr)
            replaced = True
            break
    if not replaced:
        raise ValueError("DT_GNU_HASH dynamic tag disappeared")
    return bytes(d), nchain, nbucket


def drop_gnu_hash(elf):
    fmt, step = ("<qQ", 16) if elf.is64 else ("<iI", 8)
    entries = list(elf.dynamic_entries())
    gnu = [i for i, (_, tag, _) in enumerate(entries) if tag == DT_GNU_HASH]
    if not gnu:
        return
    last, index = len(entries) - 2, gnu[0]
    if index != last:
        struct.pack_into(fmt, elf.data, entries[index][0], entries[last][1], entries[last][2])
    struct.pack_into(fmt, elf.data, entries[last][0], DT_NULL, 0)


def rebuild_sysv_hash(data):
    elf = Elf(data)
    tags = elf.dynamic()
    symtab_off = elf.v2o(tags[DT_SYMTAB])
    strtab_off = elf.v2o(tags[DT_STRTAB])
    syment = tags.get(DT_SYMENT, 24 if elf.is64 else 16)

    if DT_HASH not in tags:
        if DT_GNU_HASH not in tags:
            raise ValueError("ELF has neither DT_HASH nor DT_GNU_HASH")
        return convert_gnu_hash_to_sysv(elf, tags)

    drop_gnu_hash(elf)
    tags = elf.dynamic()
    hash_off = elf.v2o(tags[DT_HASH])
    nbucket, nchain = struct.unpack_from("<II", elf.data, hash_off)
    build_sysv_table(elf, hash_off, nchain, nbucket, symtab_off, strtab_off, syment)
    return bytes(elf.data), nchain, nbucket


def hash_lookup(data, name):
    elf = Elf(data)
    tags = elf.dynamic()
    hash_off = elf.v2o(tags[DT_HASH])
    symtab_off = elf.v2o(tags[DT_SYMTAB])
    strtab_off = elf.v2o(tags[DT_STRTAB])
    syment = tags.get(DT_SYMENT, 24 if elf.is64 else 16)
    d = elf.data
    nbucket, nchain = struct.unpack_from("<II", d, hash_off)
    bucket_off = hash_off + 8
    chain_off = bucket_off + nbucket * 4
    index = struct.unpack_from("<I", d, bucket_off + (elf_hash(name) % nbucket) * 4)[0]
    while index:
        if symbol_name(d, symtab_off, strtab_off, syment, index) == name:
            return True
        index = struct.unpack_from("<I", d, chain_off + index * 4)[0]
    return False


def rewrite_classes_jar(path, report):
    with zipfile.ZipFile(path) as src:
        items = [(i, src.read(i.filename)) for i in src.infolist()]
    patched = []
    for info, data in items:
        name = info.filename
        if name.endswith(".class"):
            data, count = substitute(data, CLASS_SUBS)
            report["class_subs"] += count
        if name.startswith("go/"):
            name = "pg/" + name[3:]
            report["moved"] += 1
        patched.append((name, data))
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as out:
        for name, data in patched:
            out.writestr(name, data)


def main(argv):
    if len(argv) != 2:
        raise SystemExit(__doc__)
    source = os.path.abspath(argv[1])
    if not source.endswith(".aar"):
        raise SystemExit("expected an .aar")
    target = source[:-4] + "-ns.aar"
    work = tempfile.mkdtemp(prefix="xray-ns-")
    report = {"class_subs": 0, "moved": 0, "so": []}
    try:
        with zipfile.ZipFile(source) as archive:
            archive.extractall(work)
        rewrite_classes_jar(os.path.join(work, "classes.jar"), report)

        jni = os.path.join(work, "jni")
        for abi in sorted(os.listdir(jni)) if os.path.isdir(jni) else []:
            old_so = os.path.join(jni, abi, f"lib{OLD_LIB}.so")
            if not os.path.exists(old_so):
                continue
            data = open(old_so, "rb").read()
            if has_soname(data):
                raise ValueError(f"{abi}: DT_SONAME exists; manual handling is required")
            data, count = substitute(data, SO_SUBS)
            data, nchain, nbucket = rebuild_sysv_hash(data)
            for probe in (b"Java_pg_Seq_init", b"Java_pg_Seq_setContext"):
                if not hash_lookup(data, probe):
                    raise ValueError(f"{abi}: {probe.decode()} is not reachable through DT_HASH")
            if hash_lookup(data, b"Java_go_Seq_init"):
                raise ValueError(f"{abi}: old Java_go_Seq_init remains resolvable")
            with open(os.path.join(jni, abi, f"lib{NEW_LIB}.so"), "wb") as output:
                output.write(data)
            os.remove(old_so)
            report["so"].append((abi, count, nchain, nbucket))

        rules = os.path.join(work, "proguard.txt")
        if os.path.exists(rules):
            with open(rules, encoding="utf-8") as source_rules:
                text = source_rules.read()
            with open(rules, "w", encoding="utf-8") as output_rules:
                output_rules.write(text.replace("class go.", "class pg."))

        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
            for root, _, files in os.walk(work):
                for filename in files:
                    full = os.path.join(root, filename)
                    out.write(full, os.path.relpath(full, work).replace(os.sep, "/"))
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print(f"wrote {target}")
    print(f"  class substitutions: {report['class_subs']}")
    print(f"  moved go/ classes: {report['moved']}")
    for abi, count, nchain, nbucket in report["so"]:
        print(f"  {abi}: native substitutions={count}, symbols={nchain}, SysV buckets={nbucket}")

    leftovers = []
    with zipfile.ZipFile(target) as archive:
        names = archive.namelist()
        if any(name.startswith("jni/") and name.endswith(f"lib{OLD_LIB}.so") for name in names):
            leftovers.append("old libgojni.so remains")
        with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as jar:
            if any(name.startswith("go/") for name in jar.namelist()):
                leftovers.append("go/ classes remain")
            for name in jar.namelist():
                if name.endswith(".class") and re.search(rb"go/(Seq|Universe|error)", jar.read(name)):
                    leftovers.append(f"old Go runtime reference in {name}")
    if leftovers:
        raise SystemExit("verification failed: " + "; ".join(leftovers))
    print("  verification: clean")


if __name__ == "__main__":
    main(sys.argv)
