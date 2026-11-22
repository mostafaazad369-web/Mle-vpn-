# MLE VPN

Independent Android VPN client built with Kotlin, Jetpack Compose and sing-box/libbox. The implementation is independently maintained; Aether Mobile is an architectural reference, not a source-code dependency.

## Implemented foundation

- Compose app with Persian UI, profile import, server list and connection status.
- sing-box/libbox 1.14.4 dependency and native runtime initialization.
- `CommandServer` lifecycle adapter with configuration validation and serialized start/stop.
- Android `VpnService` platform adapter: TUN creation, route/DNS configuration, socket protection and physical-network discovery.
- VLESS, VMess, Trojan, Shadowsocks, Hysteria2 and TUIC outbound conversion (unsupported variants fail closed).
- AES-256-GCM profile vault backed by Android Keystore.
- HTTPS subscription fetching, endpoint probes and connection verification.
- Unit tests for profile parsing, outbound conversion, config building and tunnel lifecycle.

## Current limits

A successful Gradle build or native service start is not proof of a working VPN. The app must still pass physical-device tests for real HTTPS traffic, DNS and IPv6 leaks, network handover, process death, reconnect and Android foreground-service behavior. WARP/MASQUE, Tor, Psiphon, Xray bundling, multi-hop chains, per-app routing UI and kill-switch behavior are not yet release-verified.

## Architecture

```text
Compose UI → Android VpnService → sing-box/libbox CommandServer
                                  ├─ Android TUN PlatformInterface
                                  ├─ protected upstream sockets
                                  └─ selected sing-box outbound
```

The Android service is the single TUN owner. The libbox platform interface creates and retains the descriptor, configures routes/DNS, and protects upstream sockets to avoid routing loops. Tunnel-active and internet-verified are separate states.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/ENGINE_INTEGRATION_PLAN.md](docs/ENGINE_INTEGRATION_PLAN.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Build

Requires JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

GitHub Actions runs the same unit-test and debug-APK tasks. Download the debug APK from a successful workflow artifact.

## Security and licensing

Never share profile links or exported configs containing credentials. Secrets are stored in the Android Keystore-backed encrypted profile vault; logs must not contain profile URIs, credentials, private keys or packet payloads.

MLE VPN is licensed under AGPL-3.0-or-later. Review [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Every native engine/binary added later must have its exact version, source, license and redistribution obligations recorded before packaging.
