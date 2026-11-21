# MLE VPN

An Android VPN client with a custom Kotlin/Jetpack Compose interface and mutually-exclusive sing-box 1.14.4, Xray (libv2ray) and AmneziaWG data-plane adapters.

## Brand
- App name: MLE VPN
- Deep navy: #101426
- Turquoise: #42E8D5
- Violet: #8D7CFF
- Custom vector launcher icon

## Implemented
- Persian RTL Compose interface, profile manager and an engine inventory screen with distinct per-engine glyphs and honest runtime states
- Encrypted local profile storage using Android Keystore AES-GCM
- VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, AnyTLS, SOCKS and HTTP outbound conversion for supported link variants; standard WireGuard `.conf`, `wireguard://`/`wg://`, registered Cloudflare WARP and supported OpenVPN TLS `.ovpn` profiles route through sing-box 1.14 endpoints; AmneziaWG `.conf` profiles use the official native tunnel module
- Per-profile TCP endpoint preflight diagnostics
- Regression tests for VLESS transport parameter preservation, duplicate imports, unsupported transport rejection and AmneziaWG config import/storage
- Android VPN permission flow
- Native `Libbox.checkConfig` validation before sing-box startup
- Selectable Xray `libv2ray` runtime with Android TUN fd handoff and Xray tun inbound
- Xray outbound conversion for VLESS, VMess, Trojan and Shadowsocks supported variants
- SHA-256-pinned Xray AAR download and gomobile namespace repackaging during Gradle preBuild
- Official AmneziaWG Android tunnel module pinned as a submodule, built with AGP 8.13 / Gradle 8.13 / SDK 36 / NDK 26.1.10909125 / CMake 3.22.1, and hosted in a separate `:awg` process to isolate its Go runtime from Xray
- libbox initialization, CommandServer lifecycle wrapper and Android PlatformInterface adapter
- Android TUN setup with route/DNS configuration, protected outbound sockets and descriptor lifecycle handling
- Start and stop actions from the home screen
- GitHub Actions workflow for unit tests and debug APK artifact generation

## Current limitations
- Supported protocol variants are limited to those explicitly accepted by the builders. Standard WireGuard `.conf` and share-link imports accept validated keys, addresses, endpoints, allowed IPs, DNS, MTU, keepalive and reserved bytes. AmneziaWG `.conf` files with Jc/Jmin/Jmax/S1/S2/H1-H4/I1-I5 are parsed by the pinned official config parser and routed to the isolated AWG runtime. OpenVPN import currently accepts TLS client profiles with inline CA, optional paired client cert/key and inline tls-auth/tls-crypt; external credential files, scripts/plugins, compression and static-key mode are rejected. Unsupported AmneziaWG directives are rejected by the official parser. AWG still requires physical-device handshake, route, DNS/IPv6 leak, MTU, process-death and network-handover verification. WireGuard device routing still needs physical-device validation. Current V2Ray transports include WebSocket, gRPC, HTTP and HTTPUpgrade. QUIC, XHTTP and unsupported security options are rejected rather than silently downgraded. Additional profiles are tried only when automatic failover is enabled, and only after end-to-end HTTPS verification.
- Curated public feeds are taken from MLM's own FLUX, Quick Connect and Amnezia catalogs: FLUX VLESS/Trojan/Hysteria2, Quick Connect Global plus Verified/Fast/Secure/All feeds, and Amnezia Hysteria2, WireGuard-link and registered-WARP collectors. Compatible profiles are refreshed quietly at startup and by a six-hour WorkManager job, with bounded imports, retry backoff, HTTP 304 revalidation and local encrypted profile storage. MLM's private shared Quick Connect Worker pool is not mirrored because its endpoint and per-install signing credentials are not public. Source names and feed URLs are not presented as a user-facing source directory. Community nodes are untrusted and may be offline; imported profiles are only candidates until end-to-end HTTPS verification succeeds.
- Telegram public-preview source fetching exists in the codebase, but it is not exposed as a user-facing source browser in the current silent-source branch; private channels and Telegram account login are not supported.
- Selected MLM resources are bundled under `app/src/main/assets/mlm/`; the MAE registry is parsed in-app and a per-app VPN allow-list is available for sing-box and Xray. This is basic split tunneling, not the full adaptive MAE engine. Cloudflare Worker files remain source-only until deployed to an account owned by the user.
- Cloudflare WARP profile import is supported only for a user-supplied registered WARP WireGuard config; automatic Cloudflare account registration and WARP API provisioning are not implemented.
- AmneziaWG uses the official Apache-2.0 Android tunnel module pinned at `vendor/amneziawg-android` (upstream commit `ff15093bf3856fea0947923474870b86efb682f0`). Its Go runtime and official `GoBackend.VpnService` run with the host service in `:awg`, separate from Xray's Go runtime. The app serializes engine start/stop and validates internet over the active tunnel. Physical-device handshake, routing, DNS/IPv6 leak, MTU, process-death and network-handover tests remain. See [AmneziaWG integration status](docs/AMNEZIAWG_ENGINE_INTEGRATION.md).
- OpenVPN profile support uses the sing-box 1.14.4 OpenVPN client endpoint; it is not a separate OpenVPN native runtime. Device handshake, DNS and leak validation remain. Psiphon, Tor and Geph still require their own authorized runtimes, licensing review and device tests.
- Optional root-only libbox features are intentionally disabled.
- Real-device tests for Android API levels, DNS behavior, traffic routing, battery/background behavior, and leak protection are still required.
- No server profile is guaranteed to work on a restricted network. Success depends on server reachability, protocol compatibility, DNS, carrier policy and routing.
- A successful build or CommandServer startup alone does not prove end-to-end VPN connectivity.

## Multi-engine upgrade roadmap

Development continues on the `feature/silent-auto-source-failover` branch without replacing the existing sing-box runtime.

### Runtime status (honest capability list)

- **Available in code:** sing-box/libbox, Xray/libv2ray and AmneziaWG adapters. sing-box conversion currently covers VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, AnyTLS, SOCKS, HTTP, standard WireGuard `.conf`, imported Cloudflare WARP `.conf`, and supported OpenVPN TLS `.ovpn` endpoint variants explicitly handled by its builders. Xray conversion covers VLESS, VMess, Trojan and Shadowsocks. The silent-source branch refreshes compatible public-feed profiles in the background; source names and feed URLs are not shown in a source-browser screen.
- **Partial tools:** MAE service hints and per-app split tunneling are integrated; the full adaptive per-network route learner, live service probes and domain-level policy engine remain unfinished.
- **Not yet connectable as separate engines:** FLUX as an independent runtime, WARP/Aether, native OpenVPN runtime, Psiphon, Tor, Geph, Lantern, URnetwork, Proton, Windscribe and FENIX. AmneziaWG's official native library and isolated Android tunnel service are integrated; physical-device verification remains. Each still needs its own reviewed runtime/auth flow or a verified mapping through an existing core.
- **Companion tools not yet integrated:** Gereh messenger, Cloud panel, Config Studio runtime, Cloudflare Doctor probes, MLM Scout live ranking, Game Booster, GitHub Tunnel, Telegram Proxy, LAN sharing, signed engine store and backup/restore. Their public resource files are bundled separately and are not operational by themselves.

### Target architecture

1. Ensure exactly one selected engine establishes the active Android TUN. sing-box/Xray use MLE's `MleVpnService`; AmneziaWG delegates TUN ownership to its official `GoBackend.VpnService` while MLE orchestrates its lifecycle.
2. Put each packet engine behind a common adapter boundary; never let two engines compete for the same TUN.
3. `MleEngineManager` owns the active data-plane adapter; sing-box, Xray and AmneziaWG adapters are registered and mutually exclusive.
4. The silent-source/failover branch prioritizes the user's selected profile, then source-managed profiles and other saved profiles, and can examine up to 25 candidates when automatic failover is enabled; a candidate is accepted only after a real HTTPS request succeeds through the VPN tunnel.
5. Probe candidates using their own transport and then verify a real request through the candidate route.
6. Rank saved profiles using locally stored success history, consecutive failures and an EWMA of end-to-end verification duration. This first history store is per installation, not yet partitioned by underlying network; real RTT/throughput probes and network-specific learning remain future work.
7. Apply hard requirements (for example UDP or IPv6) before ranking, and add cooldown/hysteresis before automatic route switching.
8. Verify traffic and DNS/IPv4/IPv6 leak behavior on real Android devices before marking a release production-ready.

The full MLM feature inventory and status are tracked in [docs/MLM_FEATURE_PARITY_CHECKLIST.md](docs/MLM_FEATURE_PARITY_CHECKLIST.md); keep this list updated as features are implemented.

The planner must never treat a TCP port-open result, a successful JSON schema check, or TUN creation alone as proof of a working VPN. Credentials and private keys must never be hardcoded or written to logs. Xray and sing-box share one Android VpnService TUN owner; only one data-plane engine may run at a time.

### Licensing

The existing libbox dependency is GPL-3.0. Any additional engine must be reviewed for compatible licensing, redistribution terms and required attribution before it is bundled into an APK. MLM VPN source code must not be copied unless its licence and attribution conditions are met.

## Build
Use Android Studio with JDK 17, Android SDK Platform 36, Gradle 8.13, Android Gradle Plugin 8.13, NDK 26.1.10909125, CMake 3.22.1 and Python 3. Gradle downloads the pinned Xray AAR, verifies its SHA-256 checksum, then isolates its gomobile namespace using the attributed build script. The generated AAR is stored under `app/build/` and is not committed. The GitHub Actions workflow runs unit tests and uploads a debug APK artifact after a successful build.

## Runtime notes
See [sing-box runtime integration](docs/SINGBOX_RUNTIME_INTEGRATION.md) and [endpoint preflight diagnostics](docs/ENDPOINT_PREFLIGHT.md).

## Third-party licensing
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The libbox dependency is GPL-3.0; redistributions must comply with its license and source-availability requirements.


## Attribution
The route-selection and adaptive-failover design was informed by the public FLUX/MAE architecture documents in [MLM VPN](https://github.com/mlmvpn/mlmvpn_android). The AAR namespace-isolation script is adapted from MLM's GPL-3.0 build tooling with attribution preserved in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). MLE VPN remains an independent implementation and does not bundle MLM VPN native binaries.
