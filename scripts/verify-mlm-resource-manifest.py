#!/usr/bin/env python3
"""Verify the imported MLM VPN asset inventory and keep sensitive seed files out."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "mlm"
MANIFEST = ASSETS / "RESOURCE_MANIFEST.json"

PRIVATE_KEY_ASSIGNMENT = re.compile(
    r"(?im)^\s*(?:PrivateKey|private_key)\s*=\s*[A-Za-z0-9+/]{40,}={0,2}\s*$"
)
PEM_PRIVATE_KEY = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
)

def fail(message: str) -> None:
    print(f"MLM asset verification failed: {message}", file=sys.stderr)
    raise SystemExit(1)

def main() -> None:
    if not MANIFEST.is_file():
        fail(f"missing {MANIFEST.relative_to(ROOT)}")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("upstream") != "https://github.com/mlmvpn/mlmvpn_android":
        fail("unexpected upstream repository")
    entries = manifest.get("assets")
    if not isinstance(entries, list) or not entries:
        fail("manifest has no assets")

    missing: list[str] = []
    for entry in entries:
        rel = entry.get("path")
        if not isinstance(rel, str) or not rel:
            fail(f"invalid path entry: {entry!r}")
        target = (ASSETS / rel).resolve()
        if ASSETS.resolve() not in target.parents and target != ASSETS.resolve():
            fail(f"path escapes MLM asset directory: {rel}")
        if not target.exists():
            missing.append(rel)
    if missing:
        fail("manifest references missing assets: " + ", ".join(missing))

    if (ASSETS / "amnezia_seed.json").exists():
        fail("sensitive amnezia_seed.json must not be bundled")
    if list(ASSETS.rglob("*.ovpn")):
        fail("OpenVPN sample profiles are excluded pending credential/license review")

    for path in ASSETS.rglob("*"):
        if not path.is_file() or path.name == "RESOURCE_MANIFEST.json":
            continue
        if path.stat().st_size > 2_000_000:
            fail(f"unexpectedly large bundled resource: {path.relative_to(ASSETS)}")
        try:
            data = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if PRIVATE_KEY_ASSIGNMENT.search(data) or PEM_PRIVATE_KEY.search(data):
            fail(f"private key material detected in {path.relative_to(ASSETS)}")

    print(f"Verified {len(entries)} MLM manifest entries; no excluded private-key seeds or OpenVPN profiles found.")

if __name__ == "__main__":
    main()
