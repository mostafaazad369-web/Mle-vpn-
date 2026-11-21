# AmneziaWG Android engine integration status

## Pinned upstream source

The official Android implementation is pinned as a Git submodule:

- Repository: https://github.com/amnezia-vpn/amneziawg-android
- Upstream commit: `ff15093bf3856fea0947923474870b86efb682f0`
- Local path: `vendor/amneziawg-android`
- License: Apache-2.0; preserve upstream copyright/SPDX notices and the licenses of nested submodules.

## Integrated runtime path

- The upstream `:tunnel` module is included as `:awg-tunnel` and linked into the MLE APK.
- The app build uses AGP 8.13.0, Gradle 8.13, compileSdk 36, NDK 26.1.10909125 and CMake 3.22.1 to match the pinned upstream toolchain.
- `AmneziaWgConfigParser` delegates config validation to the official `org.amnezia.awg.config.Config` parser. It preserves the original AWG directives and stores the complete config in the existing Android Keystore AES-GCM profile vault using an `awg-conf://` envelope.
- `AmneziaWgEngineAdapter` communicates with `AwgHostService` through a private Messenger channel. The raw config is not logged.
- `AwgHostService` and the official `GoBackend.VpnService` share the isolated `:awg` process. This keeps AmneziaWG's gomobile Go runtime separate from Xray's Go runtime in the main process.
- `MleEngineManager` serializes start/stop and registers AmneziaWG as a separate engine. AmneziaWG profiles always select this adapter and are never converted to standard WireGuard or sing-box.
- The orchestrator verifies real HTTPS connectivity after the AWG tunnel is started. A successful native start alone is not reported as verified internet.

## Important ownership detail

The official AmneziaWG backend establishes its own Android TUN through `GoBackend.VpnService`. For AWG profiles, MLE's `MleVpnService` acts as the foreground session/orchestration service and does not start sing-box or Xray; the AWG backend is the active TUN provider. EngineManager ensures only one data-plane adapter is active at a time. Do not start an MLE sing-box/Xray TUN alongside the AWG backend.

## Validation still required

- CI must complete a fresh native build and unit-test run on the exact current branch head.
- Confirm all intended APK ABIs and 16 KB ELF page alignment in the generated APK.
- Physical-device tests: VPN permission/revocation, start/stop, failed-start cleanup, process death, reboot behavior, Wi-Fi/mobile handover, DNS, IPv4/IPv6 routes and leak protection, UDP, MTU, handshake and sustained traffic.
- The AWG runtime remains marked adapter-in-progress until those device checks pass.

## Safety and licensing

AmneziaWG's obfuscation parameters (Jc/Jmin/Jmax/S1/S2/H1-H4/I1-I5) are preserved and validated by the official parser. The importer must never strip them or downgrade them to ordinary WireGuard. Preserve Apache-2.0 notices and all nested submodule licenses. No MLM private Worker endpoint or credentials are reused.
