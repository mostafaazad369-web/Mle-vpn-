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
- VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, AnyTLS, SOCKS and HTTP outbound conversion for supported link variants
- Per-profile TCP endpoint preflight diagnostics
- Regression tests for VLESS transport parameter preservation, duplicate imports, and unsupported transport rejection
- Android VPN permission flow
- Native `Libbox.checkConfig` validation before startup
- libbox initialization, CommandServer lifecycle wrapper and Android PlatformInterface adapter
- Android TUN setup with route/DNS configuration, protected outbound sockets and descriptor lifecycle handling
- Start and stop actions from the home screen
- GitHub Actions workflow for unit tests and debug APK artifact generation

## Current limitations
- Supported protocol variants are limited to those explicitly accepted by `SingBoxOutboundBuilder`; current V2Ray transports include WebSocket, gRPC, HTTP and HTTPUpgrade. QUIC, XHTTP and unsupported security options are rejected rather than silently downgraded. Additional profiles are tried only when automatic failover is enabled, and only after end-to-end HTTPS verification.
- The 100 Telegram channel sources and additional engines requested for future versions are not yet integrated.
- Optional root-only libbox features are intentionally disabled.
- Real-device tests for Android API levels, DNS behavior, traffic routing, battery/background behavior, and leak protection are still required.
- No server profile is guaranteed to work on a restricted network. Success depends on server reachability, protocol compatibility, DNS, carrier policy and routing.
- A successful build or CommandServer startup alone does not prove end-to-end VPN connectivity.

## Multi-engine upgrade roadmap

Development is being extended on the `feature/mlm-engine-upgrade` branch without replacing the existing sing-box runtime.

### Runtime status (honest capability list)

- **Available:** the existing sing-box/libbox engine and the profile formats accepted by the current converter.
- **Planned, not yet connectable:** Xray, WireGuard/WARP, AmneziaWG, OpenVPN, Psiphon, Tor and Geph. These require their own compatible runtime adapters, dependency/ABI checks, lifecycle integration and device testing before they can be exposed as working connection options.

### Target architecture

1. Keep Android `VpnService` as the single owner of the device TUN.
2. Put each packet engine behind a common adapter boundary; never let two engines compete for the same TUN.
3. `MleEngineManager` owns the active data-plane adapter; currently only the existing sing-box adapter is registered. The selected Android `VpnService` remains the single TUN owner.
4. The first fallback pass tries up to three saved sing-box profiles, beginning with the user's selected profile; a candidate is accepted only after a real HTTPS request succeeds through the VPN tunnel.
5. Probe candidates using their own transport and then verify a real request through the candidate route.
6. Rank saved profiles using locally stored success history, consecutive failures and an EWMA of end-to-end verification duration. This first history store is per installation, not yet partitioned by underlying network; real RTT/throughput probes and network-specific learning remain future work.
7. Apply hard requirements (for example UDP or IPv6) before ranking, and add cooldown/hysteresis before automatic route switching.
8. Verify traffic and DNS/IPv4/IPv6 leak behavior on real Android devices before marking a release production-ready.

The planner must never treat a TCP port-open result, a successful JSON schema check, or TUN creation alone as proof of a working VPN. Credentials and private keys must never be hardcoded or written to logs.

### Licensing

The existing libbox dependency is GPL-3.0. Any additional engine must be reviewed for compatible licensing, redistribution terms and required attribution before it is bundled into an APK. MLM VPN source code must not be copied unless its licence and attribution conditions are met.

## Build
Use Android Studio with JDK 17, Android SDK Platform 35 and Gradle 8.9 or newer compatible with the Android Gradle Plugin. The repository also includes a GitHub Actions workflow that runs unit tests and uploads a debug APK artifact after a successful build.

## Runtime notes
See [sing-box runtime integration](docs/SINGBOX_RUNTIME_INTEGRATION.md) and [endpoint preflight diagnostics](docs/ENDPOINT_PREFLIGHT.md).

## Third-party licensing
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The libbox dependency is GPL-3.0; redistributions must comply with its license and source-availability requirements.
