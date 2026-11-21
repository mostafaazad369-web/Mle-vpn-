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
- Initial VLESS, VMess, Trojan and supported sing-box share-link conversion
- Standard WireGuard conf import using the sing-box 1.14+ WireGuard endpoint schema; AmneziaWG obfuscation directives fail closed until its native runtime is integrated
- Public HTTPS config feed registry with primary/mirror fallback, Base64 handling, per-feed import and all-feed add-only refresh
- Existing encrypted user profiles preserved during public-feed refresh
- Engine inventory screen with distinct glyphs and honest catalog-only/experimental states
- Public Telegram preview source screen
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
- Public feed refresh is user-triggered; scheduled background refresh, persistent source cache/TTL and feed health history are not implemented yet.
- Only the existing sing-box runtime is connected to the VPN service. Standard WireGuard conf profiles are routed through its WireGuard endpoint (not the removed legacy WireGuard outbound). Automatic WARP registration and AmneziaWG obfuscation/native runtime are not integrated; other MLM engine families remain catalog entries.
- Public feeds may contain expired, malicious, duplicate or unsupported nodes; imported links are filtered by the current sing-box outbound builder, but a successful import does not guarantee connectivity.
- Optional root-only libbox features are intentionally disabled.
- Real-device tests for Android API levels, DNS behavior, traffic routing, battery/background behavior, and leak protection are still required.
- No server profile is guaranteed to work on a restricted network. Success depends on server reachability, protocol compatibility, DNS, carrier policy and routing.
- A successful build or CommandServer startup alone does not prove end-to-end VPN connectivity.

## Build
Use Android Studio with JDK 17, Android SDK Platform 35 and Gradle 8.9 or newer compatible with the Android Gradle Plugin. Run `./gradlew testDebugUnitTest assembleDebug` to verify JVM tests and build a debug APK. Device-level VPN and leak tests must still be performed on real Android hardware.

## Runtime notes
See [sing-box runtime integration](docs/SINGBOX_RUNTIME_INTEGRATION.md) and [endpoint preflight diagnostics](docs/ENDPOINT_PREFLIGHT.md).

## Third-party licensing
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The libbox dependency is GPL-3.0; redistributions must comply with its license and source-availability requirements.
