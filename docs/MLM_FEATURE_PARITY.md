# MLM VPN feature-parity integration plan

This branch tracks a staged integration of selected MLM VPN capabilities into MLE VPN.

## Upstream and licensing

- Upstream source: https://github.com/mlmvpn/mlmvpn_android
- Upstream release inspected: source commit c56b89f7007776e725c9a5d4c8b52fa033af150f (2026-11-01).
- Upstream is GPL-3.0 with an additional attribution condition in its LICENSE (GPLv3 section 7). Preserve the upstream LICENSE, copyright notices, source notices, and clearly credit **mlmvpn** in the app's About screen and distribution materials.
- MLE VPN already contains GPL-3.0 and attribution notices; verify the exact text and UI placement before shipping copied/derived code.

## Integration rule

Do not copy the upstream Android app wholesale: it has a separate application architecture, a large Telegram-derived Gereh client, native components, and thousands of files. Port features into MLE's existing Kotlin/Compose + sing-box architecture behind stable interfaces. Preserve existing MLE functionality.

Do not import upstream placeholder server addresses, redacted relay keys, user credentials, or assume that public source code contains usable production infrastructure. Users must configure their own Cloudflare/GitHub/provider accounts where required. Do not bundle private keys or account tokens.

## Feature inventory and intended order

### Foundation
- [ ] Multi-engine coordinator: sing-box and AmneziaWG, exactly one active VPN engine.
- [ ] Common engine lifecycle, state, logs, traffic stats, permission flow, and crash recovery.
- [ ] Profile/subscription storage, encrypted secrets, backup/restore, import/export.
- [ ] Shared server catalog and capability registry; no fake/placeholder endpoints presented as live servers.

### Connection engines and transports
- [ ] AmneziaWG / WireGuard and Amnezia server catalogue/parser.
- [ ] FLUX adaptive route selection and verified exit probing.
- [ ] MAE per-app/per-domain adaptive routing and Cloudflare egress (user-owned account).
- [ ] WARP, MASQUE, WARP-in-WARP and Aether integration.
- [ ] V2Ray/Xray protocol coverage: VLESS, VMess, Trojan, Shadowsocks, REALITY, TLS, WS, gRPC, XHTTP.
- [ ] Hysteria2, OpenVPN, Outline, NPV, Psiphon, Tor bridge management, Geph, Lantern, URnetwork, SoftEther/VPNGate.
- [ ] Provider integrations: FENIX, Proton VPN and Windscribe, subject to their current APIs, terms, and user-owned accounts.
- [ ] Telegram proxy, GitHub Tunnel, Quick Connect, SNI/ECH and domain-fronting routes, only where technically and legally supportable.

### Cloud and network tools
- [ ] Cloudflare account connection, Worker deployment, config sync and usage view.
- [ ] Cloudflare Doctor diagnostics and per-network strategy memory.
- [ ] Config Studio / subscription generator and user/device/volume limits.
- [ ] MLM Scout real route tests and ranking.
- [ ] Game Booster, game-region latency, per-game DNS and ping overlay.
- [ ] Smart Iran routing, split tunnelling, LAN proxy/hotspot/PAC sharing.
- [ ] DNS, geoip/geosite and sanctioned-domain data with provenance and update policy.

### Resources and companion features
- [ ] Review upstream app assets individually: worker scripts, country/IP datasets, game catalog, Iran profiles, Tor bridge seeds, OpenVPN examples/licenses, exit-proxy metadata, and other JSON/text resources.
- [ ] Copy only resources whose license and provenance are clear; keep original notices and add attribution.
- [ ] Rebuild or adapt scripts to MLE interfaces; do not assume a worker asset is functional without its own Cloudflare deployment/config.
- [ ] Evaluate Gereh messenger (Rust core + Telegram-derived UI) as a separate optional module, not as a VPN engine. It requires its own dependency, process, storage, permission, and GPL review.

## Validation gates for each port
1. Compile and lint the affected Android modules.
2. Unit tests for parsers, config builders, routing and lifecycle.
3. Verify manifest permissions, service declarations, ABI/native packaging and 16 KB page alignment where applicable.
4. Confirm engine mutual exclusion and clean disconnect/reconnect behavior.
5. Run CI APK build and record artifact + commit.
6. Test on a real Android device and clearly separate "compiled" from "connected successfully".

## Important upstream limitation

The upstream README says production server/relay addresses and keys were deliberately replaced with placeholders and prebuilt binaries are omitted from source. Therefore copying all source/assets cannot by itself recreate upstream-operated services or guarantee live servers. Operational integrations need valid user-owned credentials, deployable infrastructure, or independently verified public sources.
