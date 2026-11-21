# MLM VPN feature-parity checklist for MLE VPN

Reference: https://github.com/mlmvpn/mlmvpn_android

This register separates connection engines, protocol support, account/provider integrations and companion tools. A name here is not a claim that a feature is ready.

## Status key
- [x] Implemented in code and covered by CI; not necessarily verified on physical devices.
- [~] Partial support through an existing core/import path.
- [ ] Not implemented.
- [!] Requires provider authorization, account, server/profile, licensing clearance or device validation.

## Engines and connection methods

| Feature | Status | Work remaining |
|---|---|---|
| sing-box / libbox | [x] | Expand verified profiles; DNS/IPv6 leak and device tests |
| Xray / libv2ray | [x] | VLESS, VMess, Trojan, Shadowsocks mapped; XHTTP and device validation |
| FLUX adaptive route planner | [~] | Health-ranked fallback across up to ten saved routes and real HTTPS exit verification are implemented; parallel candidate racing, country/IP preferences, standby routes and per-network learning remain |
| MAE per-app adaptive routing | [~] | Upstream service registry is parsed and shown; app-level include allow-list is implemented for sing-box and Xray. Live network-specific probes, adaptive classifier, domain-level policy compiler and balancer remain |

| Cloudflare WARP | [~] | Detects user-supplied registered WARP WireGuard configs and routes via sing-box; automatic registration, WARP API provisioning and device tests remain |
| WARP-in-WARP | [ ] | Nested route orchestration, MTU and loop validation |
| Aether / MASQUE | [ ] | Permitted MASQUE/WireGuard runtime and ABI review |
| AmneziaWG | [~] | Official Apache-2.0 Android source pinned as submodule; native Go/CMake build, one-TUN lifecycle, parser/runtime adapter and ABI/device tests remain |
| WireGuard | [~] | Standard .conf import and sing-box endpoint config implemented; physical-device routing, DNS and leak validation remain |
| Hysteria 2 | [~] | sing-box share-link mapping; expand fixtures and UDP device tests |
| TUIC | [~] | sing-box share-link mapping; expand fixtures and UDP tests |
| Outline / Shadowsocks | [~] | Shadowsocks URI supported; Outline access-key variants remain |
| OpenVPN | [ ] | .ovpn parser, cert/key handling, approved core and lifecycle |
| VPN Gate / SoftEther | [ ] | Relay discovery and protocol adapters |
| NPV configs | [ ] | Identify formats, parser and fixtures |
| Psiphon | [!] | Official/authorized Android SDK and service terms |
| Tor | [ ] | Tor runtime, SOCKS lifecycle, bridges and DNS tests |
| Geph | [!] | Authorized SDK/runtime and service access |
| Lantern | [!] | Authorized SDK/runtime and service access |
| URnetwork | [!] | Authorized SDK/runtime and service access |
| FENIX / Firefox VPN | [!] | Verify provider integration permission and account flow |
| Proton VPN | [!] | User's official WireGuard/OpenVPN profiles and account terms |
| Windscribe | [!] | User's official WireGuard/OpenVPN profiles; Stealth/WStunnel require separate runtimes |
| SlipNet-like DNS/SSH tunnel | [!] | Do not copy SlipNet source/binaries/branding; use independently licensed implementation or written permission |

## Profile import and source handling

- [x] VLESS, VMess and Trojan import for explicitly supported variants.
- [x] Shadowsocks share links for sing-box and Xray.
- [x] Hysteria2, TUIC, AnyTLS, SOCKS and HTTP conversion for sing-box.
- [x] Batch import, encrypted local profile vault and subscription fetch.
- [~] Public Telegram preview fetch remains available internally; the source catalog is removed from the primary app navigation.
- [~] MLM/FLUX-inspired public feeds are hydrated silently in the background; raw/jsDelivr mirror fallback, bounded downloads, ETag cache and last-good cache are used. Profiles are imported only when a local engine builder accepts them; every route still requires real HTTPS verification through TUN. Source names and URLs are not shown in the app.
- [x] Standard WireGuard .conf importer, bounded file picker, encrypted-at-rest private-key storage, parser validation and sing-box endpoint config tests.
- [x] Cloudflare WARP peer-key recognition for imported registered WARP `.conf` files; WARP profiles automatically select sing-box for the data plane. Automatic account registration/provisioning is not implemented.
- [x] Explicit AmneziaWG obfuscation-field detection and fail-closed rejection; no silent downgrade to standard WireGuard.
- [~] Pin official AmneziaWG Android source at upstream commit `ff15093bf3856fea0947923474870b86efb682f0` as `vendor/amneziawg-android`; integration and runtime remain incomplete. See [docs/AMNEZIAWG_ENGINE_INTEGRATION.md](AMNEZIAWG_ENGINE_INTEGRATION.md).
- [~] Add isolated CI build of the pinned upstream `:tunnel:assembleDebug` AAR; verify successful build and packaged ABIs. The artifact is not linked to MLE APK yet.
- [ ] Integrate the built tunnel runtime with a single-VpnService lifecycle and add AWG config parser/serializer.
- [~] Basic MAE service registry display and per-app allow-list routing implemented; adaptive routing remains outstanding.
- [ ] OpenVPN .ovpn import, inline certs and credential handling.
- [ ] Outline access-key variants.
- [ ] NPV format fixtures and parser.
- [ ] Xray XHTTP mapping and tests.
- [~] Silent startup source refresh and bounded automatic fallback are implemented; periodic WorkManager scheduling, expiry, source-level telemetry and per-network health remain.

## Companion tools and app features

| Feature | Status | Work remaining |
|---|---|---|
| Gereh serverless messenger | [ ] | Independent E2E identity, discovery, relay and lifecycle design; review GPL/attribution before reuse |
| Cloud panel / Cloudflare accounts | [ ] | User-owned API token/OAuth, Worker deploy, config fetch and usage |
| Config Studio | [ ] | User/volume/time/device policy, templates and multi-location endpoints |
| Cloudflare Doctor | [ ] | Per-network edge, DNS, IPv4/IPv6 diagnostics |
| MLM Scout | [ ] | Bounded real-transport probes and honest ranking |
| Game Booster | [ ] | Game-region route probes, latency/loss and optional overlay |
| GitHub Tunnel | [ ] | User-owned deployment, quota and secrets lifecycle |
| Telegram Proxy | [ ] | User-owned Cloudflare deployment |
| Quick Connect / Iran configs | [ ] | Curated sources, consent, provenance and validation |
| SNI / ECH diagnostics | [ ] | ECH-aware DNS/client diagnostics |
| Serverless domain fronting | [ ] | User-owned domain/origin and TLS/Host correctness |
| Smart Iran routing | [ ] | Signed rule data and local/foreign policy |
| Per-app split tunneling | [~] | App picker, persistent package allow-list and sing-box/Xray TUN application filters implemented; physical-device validation remains |
| LAN proxy/hotspot/PAC sharing | [ ] | Local proxy, hotspot permissions, PAC and firewall tests |
| Home-screen widgets / Widget Studio | [ ] | Widget provider and configurable cards |
| Quick Settings tile | [ ] | TileService and state synchronization |
| Engine store / signed updates | [ ] | Signed manifests, key rotation, rollback protection |
| Backup / restore | [ ] | Encrypted export/import and secret handling |
| In-app updates | [ ] | Signed metadata, integrity and safe install |

## Completion gates

1. Review exact upstream version, license, redistribution terms and attribution.
2. Pin dependencies and checksums; never trust mutable release assets.
3. Keep exactly one Android VpnService/TUN owner; only explicit compatible local-proxy chains may be nested.
4. Encrypt credentials/private keys and redact logs.
5. Add positive and negative parser/config tests.
6. Test start, stop, revoke, process death, network handover and background lifecycle on real devices.
7. Verify HTTPS through TUN, exit IP, DNS, IPv4/IPv6 leaks, UDP, MTU and stability.
8. Expose an engine as selectable only when its adapter is registered; device-verified only after physical-device tests.

## CI baseline

GitHub Actions run 44769690782 completed both the debug APK build and JVM/Android unit-test jobs for the feature branch. CI success is not a physical-device connectivity or leak-test result.
