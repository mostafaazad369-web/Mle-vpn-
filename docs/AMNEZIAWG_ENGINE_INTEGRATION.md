# AmneziaWG Android engine integration status

## Pinned upstream source

The official Android implementation is pinned as a Git submodule:

- Repository: https://github.com/amnezia-vpn/amneziawg-android
- Upstream commit: `ff15093bf3856fea0947923474870b86efb682f0`
- Local path: `vendor/amneziawg-android`
- Submodule declaration: `.gitmodules`
- License in upstream: Apache-2.0; preserve upstream copyright/SPDX notices and the licenses of both submodules.

The submodule is source pinning only. It is not yet a Gradle module dependency, does not add an AmneziaWG engine to the runtime registry, and does not make AWG configs connectable.

## Why the native engine is not wired into MLE yet

The official source includes a `:tunnel` Android library, Java configuration parser/model, `GoBackend`, and CMake/Go native targets:
- `libwg-go.so`
- `libwg.so`
- `libwg-quick.so`

Its build currently uses Android Gradle Plugin 8.13.0, compileSdk 36, NDK 26.1.10909125 and CMake. MLE currently uses Android Gradle Plugin 8.7.3, compileSdk 35 and Gradle 8.9. A direct include without a coordinated toolchain upgrade is not a safe integration.

The official `GoBackend` uses its own `VpnService` bridge and owns tunnel creation/state. MLE's `MleVpnService` currently owns its own VPN/TUN lifecycle and selects exactly one engine adapter. Simply starting both backends risks two competing VPN services/interfaces. The AWG service integration therefore needs a single explicit owner and lifecycle bridge before an engine can be registered.

## Next implementation steps

1. Initialize the pinned submodule recursively in CI and verify both nested submodule commit pins.
2. Upgrade Gradle/AGP/SDK/NDK/CMake as one tested change, or isolate the AWG build in a reproducible composite build.
3. Build the official tunnel module for arm64-v8a and armeabi-v7a; build x86_64 for emulator CI. Verify ELF architecture and 16 KB page alignment.
4. Preserve the complete AWG config, including Jc/Jmin/Jmax/S1/S2/H1-H4/I1-I5, through parsing and native serialization. Never downgrade an AWG profile to WireGuard.
5. Choose one ownership model:
   - MLE delegates the entire VPN session to the official AWG service and serializes stop/start with its current engine; or
   - a reviewed native backend bridge transfers/uses a single MLE-owned TUN descriptor, if the upstream API supports it without forking unsafe internals.
6. Add foreground notification, permission/revocation, process death, reconnect, network handover, MTU, UDP, DNS, IPv4/IPv6 leak, handshake and byte-counter tests.
7. Register `amneziawg` in `MleEngineManager` and expose it in UI only after ABI packaging and lifecycle tests pass.

## Current behavior

- Standard WireGuard configs continue to be handled by sing-box.
- Cloudflare WARP user-supplied registered WireGuard configs continue to be recognized and routed through sing-box.
- AmneziaWG obfuscation options are detected and rejected by the ordinary WireGuard importer.
- The official AmneziaWG source is pinned, but there is no AmneziaWG runtime adapter or APK capability yet.
