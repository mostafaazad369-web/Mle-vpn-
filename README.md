# MLE VPN

An Android VPN client with a custom Kotlin/Jetpack Compose interface and selectable sing-box 1.14.4 / Xray (libv2ray) data-plane engines.

## Brand
- App name: MLE VPN
- Deep navy: #101426
- Turquoise: #42E8D5
- Violet: #8D7CFF
- Custom vector launcher icon

## Implemented
- Persian RTL Compose interface and profile manager with explicit active-profile selection
- Encrypted local profile storage using Android Keystore AES-GCM
- VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, AnyTLS, SOCKS and HTTP outbound conversion for supported link variants; standard WireGuard `.conf` import through sing-box endpoints; Cloudflare WARP `.conf` profiles are identified by the official WARP peer key and routed through sing-box
- Per-profile TCP endpoint preflight diagnostics
- Regression tests for VLESS transport parameter preservation, duplicate imports, and unsupported transport rejection
- Android VPN permission flow
- Native `Libbox.checkConfig` validation before sing-box startup
- Selectable Xray `libv2ray` runtime with Android TUN fd handoff and Xray tun inbound
- Xray outbound conversion for VLESS, VMess, Trojan and Shadowsocks supported variants
- SHA-256-pinned Xray AAR download and gomobile namespace repackaging during Gradle preBuild
- libbox initialization, CommandServer lifecycle wrapper and Android PlatformInterface adapter
- Android TUN setup with route/DNS configuration, protected outbound sockets and descriptor lifecycle handling
- Start and stop actions from the home screen
- GitHub Actions workflow for unit tests and debug APK artifact generation

## Current limitations
- Supported protocol variants are limited to those explicitly accepted by the builders. Standard WireGuard `.conf` import accepts Interface private key/address/DNS/MTU and Peer public/preshared key/endpoint/allowed IPs/keepalive/reserved fields; unsupported scripts and AmneziaWG obfuscation options are rejected. WireGuard device routing still needs physical-device validation. Current V2Ray transports include WebSocket, gRPC, HTTP and HTTPUpgrade. QUIC, XHTTP and unsupported security options are rejected rather than silently downgraded. Additional profiles are tried only when automatic failover is enabled, and only after end-to-end HTTPS verification.
- Curated public node feeds inspired by MLM FLUX/Amnezia source catalogs (including Morpheus mini/lite and FastNodes top/verified bundles) are integrated with GitHub raw/jsDelivr mirrors, bounded downloads, ETag/Last-Modified caching, offline last-good fallback and selected-engine validation; community nodes are untrusted and may be offline.
- Telegram public-preview source fetching is integrated; private channels and Telegram account login are not supported.
- Cloudflare WARP profile import is supported only for a user-supplied registered WARP WireGuard config; automatic Cloudflare account registration and WARP API provisioning are not implemented.
- AmneziaWG obfuscation parameters (Jc/Jmin/Jmax/S1/S2/H1-H4/I1-I5) are explicitly detected and rejected rather than silently downgraded to plain WireGuard. The official Amnezia Android tunnel module requires a native Go/CMake build and owns its own VpnService; a safe adapter into MLE's single-TUN lifecycle is still being implemented.
- Additional engines beyond sing-box/Xray are not yet integrated.
- Optional root-only libbox features are intentionally disabled.
- Real-device tests for Android API levels, DNS behavior, traffic routing, battery/background behavior, and leak protection are still required.
- No server profile is guaranteed to work on a restricted network. Success depends on server reachability, protocol compatibility, DNS, carrier policy and routing.
- A successful build or CommandServer startup alone does not prove end-to-end VPN connectivity.

## Multi-engine upgrade roadmap

Development is being extended on the `feature/mlm-engine-upgrade` branch without replacing the existing sing-box runtime.

### Runtime status (honest capability list)

- **Available in code:** sing-box/libbox and Xray/libv2ray adapters. sing-box conversion currently covers VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, AnyTLS, SOCKS, HTTP, standard WireGuard `.conf` and imported Cloudflare WARP `.conf` endpoint variants explicitly handled by its builders. Xray conversion covers VLESS, VMess, Trojan and Shadowsocks. Public node feeds based on MLM FLUX/Amnezia source catalogs are now exposed in the Sources screen for protocols the selected engine can convert.
- **Not yet connectable as separate engines:** FLUX, MAE, WARP/Aether, AmneziaWG, OpenVPN, Psiphon, Tor, Geph, Lantern, URnetwork, Proton, Windscribe and FENIX. Each still needs its own reviewed runtime/auth flow or a verified mapping through an existing core.
- **Companion tools not yet integrated:** Gereh messenger, Cloud panel, Config Studio, Cloudflare Doctor, MLM Scout, Game Booster, GitHub Tunnel, Telegram Proxy, LAN sharing, signed engine store and backup/restore.

### Target architecture

1. Keep Android `VpnService` as the single owner of the device TUN.
2. Put each packet engine behind a common adapter boundary; never let two engines compete for the same TUN.
3. `MleEngineManager` owns the active data-plane adapter; sing-box and Xray adapters are registered. The selected Android `VpnService` remains the single TUN owner.
4. The first fallback pass tries up to three saved sing-box profiles, beginning with the user's selected profile; a candidate is accepted only after a real HTTPS request succeeds through the VPN tunnel.
5. Probe candidates using their own transport and then verify a real request through the candidate route.
6. Rank saved profiles using locally stored success history, consecutive failures and an EWMA of end-to-end verification duration. This first history store is per installation, not yet partitioned by underlying network; real RTT/throughput probes and network-specific learning remain future work.
7. Apply hard requirements (for example UDP or IPv6) before ranking, and add cooldown/hysteresis before automatic route switching.
8. Verify traffic and DNS/IPv4/IPv6 leak behavior on real Android devices before marking a release production-ready.

The full MLM feature inventory and status are tracked in [docs/MLM_FEATURE_PARITY_CHECKLIST.md](docs/MLM_FEATURE_PARITY_CHECKLIST.md); keep this list updated as features are implemented.

The planner must never treat a TCP port-open result, a successful JSON schema check, or TUN creation alone as proof of a working VPN. Credentials and private keys must never be hardcoded or written to logs. Xray and sing-box share one Android VpnService TUN owner; only one data-plane engine may run at a time.

### Licensing

The existing libbox dependency is GPL-3.0. Any additional engine must be reviewed for compatible licensing, redistribution terms and required attribution before it is bundled into an APK. MLM VPN source code must not be copied unless its licence and attribution conditions are met.

## Build
Use Android Studio with JDK 17, Android SDK Platform 35, Gradle 8.9 or newer compatible with the Android Gradle Plugin, and Python 3. Gradle downloads the pinned Xray AAR, verifies its SHA-256 checksum, then isolates its gomobile namespace using the attributed build script. The generated AAR is stored under `app/build/` and is not committed. The GitHub Actions workflow runs unit tests and uploads a debug APK artifact after a successful build.

## Runtime notes
See [sing-box runtime integration](docs/SINGBOX_RUNTIME_INTEGRATION.md) and [endpoint preflight diagnostics](docs/ENDPOINT_PREFLIGHT.md).

## Third-party licensing
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The libbox dependency is GPL-3.0; redistributions must comply with its license and source-availability requirements.


## Attribution
The route-selection and adaptive-failover design was informed by the public FLUX/MAE architecture documents in [MLM VPN](https://github.com/mlmvpn/mlmvpn_android). The AAR namespace-isolation script is adapted from MLM's GPL-3.0 build tooling with attribution preserved in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). MLE VPN remains an independent implementation and does not bundle MLM VPN native binaries.
