# MLE VPN

An Android VPN client with a custom Kotlin/Jetpack Compose interface and a sing-box 1.14.4 core integration.

## Brand
- App name: MLE VPN
- Deep navy: #101426
- Turquoise: #42E8D5
- Violet: #8D7CFF
- Custom vector launcher icon

## Implemented
- Persian RTL Compose interface and profile manager
- Encrypted local profile storage using Android Keystore AES-GCM
- Initial VLESS, VMess and Trojan share-link conversion to sing-box JSON
- Per-profile TCP endpoint preflight diagnostics
- Android VPN permission flow
- Native `Libbox.checkConfig` validation before startup
- libbox initialization, CommandServer lifecycle wrapper and Android PlatformInterface adapter
- Android TUN setup with route/DNS configuration, protected outbound sockets and descriptor lifecycle handling
- Start and stop actions from the home screen
- GitHub Actions workflow for unit tests and debug APK artifact generation

## Current limitations
- Supported protocol variants are limited to those explicitly accepted by `SingBoxOutboundBuilder`; unsupported transport/security options are rejected.
- The 100 Telegram channel sources and additional engines requested for future versions are not yet integrated.
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
