# MLE VPN

An Android VPN client with a custom Kotlin/Jetpack Compose interface and a sing-box 1.14.4 core integration.

## Brand
- App name: MLE VPN
- Deep navy: #101426
- Turquoise: #42E8D5
- Violet: #8D7CFF
- Custom vector launcher icon

## Implemented
- Persian RTL Compose interface and profile manager with explicit active-profile selection
- Encrypted local profile storage using Android Keystore AES-GCM
- VLESS, VMess, Trojan, Shadowsocks (legacy + SIP002), Hysteria2 and TUIC share-link conversion to sing-box JSON (unsupported variants/plugins fail closed)
- Per-profile TCP endpoint preflight diagnostics
- Regression tests for VLESS transport parameter preservation, duplicate imports, and unsupported transport rejection
- Android VPN permission flow
- Native `Libbox.checkConfig` validation before startup
- libbox initialization, CommandServer lifecycle wrapper and Android PlatformInterface adapter
- Android TUN setup with route/DNS configuration, protected outbound sockets and descriptor lifecycle handling
- Start and stop actions from the home screen
- GitHub Actions workflow for unit tests and debug APK artifact generation

## Current limitations
- Supported protocol variants are limited to those explicitly accepted by `SingBoxOutboundBuilder`; unsupported transport/security options are rejected.
- The MLM-style engine catalog is present, but OpenVPN, WireGuard, WARP, MASQUE, NPV-specific formats, provider engines, Cloudflare tools and Gereh are not yet integrated as connectable backends.
- Optional root-only libbox features are intentionally disabled.
- Real-device tests for Android API levels, DNS behavior, traffic routing, battery/background behavior, and leak protection are still required.
- No server profile is guaranteed to work on a restricted network. Success depends on server reachability, protocol compatibility, DNS, carrier policy and routing.
- A successful build or CommandServer startup alone does not prove end-to-end VPN connectivity.

## Build
Use Android Studio with JDK 17, Android SDK Platform 35 and Gradle 8.9 or newer compatible with the Android Gradle Plugin. The repository also includes a GitHub Actions workflow that runs unit tests and uploads a debug APK artifact after a successful build.

## Runtime notes
See [sing-box runtime integration](docs/SINGBOX_RUNTIME_INTEGRATION.md) and [endpoint preflight diagnostics](docs/ENDPOINT_PREFLIGHT.md).

## Third-party licensing
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The libbox dependency is GPL-3.0; redistributions must comply with its license and source-availability requirements.

## MLM VPN integration (staged)

MLE VPN is developing an attributed, GPLv3-compatible integration of selected MLM VPN Android capabilities. The work is tracked on the `integration/mlmvpn-foundation` branch. This is a staged port, not a claim that every upstream engine or Gereh is already operational. See [the migration plan](docs/MLMVPN_MIGRATION.md) and the unique engine/icon inventory in `MleEngineCatalog.kt`.

Upstream credit: **mlmvpn** — https://github.com/mlmvpn/mlmvpn_android. Preserve the upstream GPLv3 license and additional attribution condition in derivative distributions.
