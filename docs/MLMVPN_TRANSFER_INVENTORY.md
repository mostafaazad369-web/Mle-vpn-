# MLMVPN → MLEVPN transfer inventory

**Audit date:** 2026-11-21  
**Upstream:** https://github.com/mlmvpn/mlmvpn_android (public main audited at `c56b89f7007776e725c9a5d4c8b52fa033af150f`)  
**Target:** https://github.com/mostafaazad369-web/Mle-vpn-  
**Working branch:** `integration/mlmvpn-foundation`  
**Baseline:** `8466b743b89751d69ca752c6e832963a88046c48`  
**PR:** [#10](https://github.com/mostafaazad369-web/Mle-vpn-/pull/10), open and Draft. Do not merge.

This is an inventory and staged engineering plan, not a claim of feature parity. Upstream README describes product scope; the upstream source tree currently exposes 933 paths beneath `app/src/main/java/com/mlmvpn`, including 375 engine-related paths and 251 UI paths. Public README, source directories and project state documents were inspected. This audit intentionally does not fetch, reproduce or embed server credentials, private account material, API tokens, relay keys, or prebuilt binary payloads.

## 1. Architecture comparison

| Area | MLMVPN public source | MLEVPN at baseline | Transfer decision |
|---|---|---|---|
| Android shell | Kotlin, Compose, multi-screen app | Kotlin, Compose, Persian RTL | Keep MLE shell, theme and navigation; add feature modules incrementally |
| Tunnel ownership | Shared app VPN service plus several core wrappers | Shared MLE `VpnService`, arbiter and adapters | Keep one TUN owner; every engine must use an adapter contract |
| Packet/data plane | Xray/V2Ray, Aether, WARP, native/provider runtimes | sing-box/libbox and OpenVPN 3 native/JNI foundation | Reuse existing runtime when protocol-compatible; isolate additional native cores |
| Profiles | Nodes, subscriptions, provider-specific stores, cloud profiles | Encrypted Keystore vault, importers and sing-box builders | Extend typed profile model; secrets stay user-owned and encrypted |
| UI identity | Engine-specific screens, home board, tools | MLE navy/turquoise/violet, Persian RTL, unique engine icon keys | Port behavior and screen structure, not upstream branding |
| Cloud/account features | Cloudflare, provider and user-deployed Worker flows | Diagnostic-only Cloudflare Doctor | Require explicit user-owned account/config setup; never ship upstream credentials |
| Messaging | Separate Rust Gereh core, Android package, relay and Telegram-derived UI | Not integrated | Separate product/security/license workstream; not a VPN engine |
| Build | Large multi-module Android app with native and prebuilt components | Gradle Kotlin DSL; Android CI and OpenVPN native workflow | Port source/dependencies selectively; no upstream APK or opaque binary copying |

## 2. Engine and protocol inventory

Status meanings: **Existing path** = code path present in MLE, not necessarily device-certified; **Foundation** = adapter/build work exists but live connection remains unverified; **Not ported** = inventory only; **Account/config gate** = depends on user-owned credentials or deployment.

| Upstream engine / method | MLE baseline status | Work / gate |
|---|---|---|
| VLESS | Existing sing-box path | Expand only with tests for each accepted transport/security variant |
| VMess | Existing sing-box path | Same; reject unsupported variants |
| Trojan | Existing sing-box path | Same; validate TLS and route behavior |
| Shadowsocks / SIP002 | Existing sing-box path | Preserve strict plugin/variant rejection |
| Outline | Existing Shadowsocks path | Validate access-key variants; no separate fake runtime |
| Hysteria2 | Existing sing-box path | Device UDP/QUIC verification pending |
| TUIC | Existing sing-box path | Device UDP/QUIC verification pending |
| WireGuard | Parser, encrypted storage and sing-box config builder | Real TUN/device verification pending |
| OpenVPN | OpenVPN 3 JNI/core build, service integration and profile import foundation | Verify provider profiles, auth, dual-stack, DNS, traffic, reconnect and TUN teardown on device |
| WARP / Cloudflare WARP | Catalog only; no connectable MLE WARP adapter | New provider/native adapter, account/registration and license/security audit |
| MASQUE | Catalog only | Carrier/runtime implementation and interoperability tests |
| WARP-in-WARP / Aether | Catalog only | Upstream Aether Rust executable source is a separate private companion; public wrapper alone is insufficient. Do not copy prebuilt releases |
| AmneziaWG | Catalog only | Native core/protocol audit, source provenance, ABI and redistribution review |
| Amnezia WireGuard / Hysteria2 | WireGuard/Hysteria2 base protocols partially represented | Amnezia-specific obfuscation/runtime still needs implementation and device tests |
| Psiphon | Catalog only | Official SDK/source availability, terms and lifecycle review |
| Tor + bridge manager | Catalog only | Tor runtime, bridge sources, isolation and device lifecycle |
| Geph | Catalog only | SDK/runtime and account requirements |
| Lantern | Catalog only | Runtime/source and redistribution terms |
| URnetwork | Catalog only | SDK/provider agreement and lifecycle |
| Proton VPN | Catalog only | User account and supported provider protocol; no account bypass |
| Windscribe | Catalog only | User account and supported provider protocol |
| FENIX / Firefox VPN | Catalog only | Confirm provider availability, account and supported integration |
| VPN Gate | Catalog only | Public discovery plus supported protocol adapter and source validation |
| NPV | Standard supported share links can enter importer; native .npvt/.npvs unsupported | Do not decrypt/execute native files or copy private data; separate documented integration |
| Quick Connect | Catalog only | Real cancellable probes, ranking and verified-through-tunnel success |
| FLUX | Catalog only | Adaptive race/scoring/route memory; upstream implementation state claims tests, but MLE needs independent port and tests |
| MAE | Catalog only | Per-app/per-network policy, route proofs, worker/exit ownership; major independent subsystem |
| Iran profiles / smart routing | Catalog/import foundation only | User-supplied profiles and reviewed public sources; avoid bundled endpoints |
| SNI / ECH / serverless fronting | Catalog only | Standards-compatible implementation, TLS/platform review and fail-closed behavior |
| GitHub Tunnel | Catalog only | User-owned infrastructure and credentials only |
| Telegram Proxy | Catalog only | User-owned Cloudflare deployment and proxy configuration |
| Free Configs / Xray scanner | Existing profile importer and endpoint preflight are narrower capabilities | Source provenance, parsing, expiry and tunnel-based health verification |
| Arena / clean-IP racing | Not ported | Separate source and network safety review |
| EDG / Gozargah / Nahan / Netra / Nova / Spider / GST / Sanction / RSTASpoof / Gateway / MLM engine modules | Not ported or partially represented by shared import/diagnostic features | Audit each module's purpose, dependencies, data collection and user-facing behavior before selection |

## 3. Tools, services and app capabilities

| Upstream subsystem | MLE baseline | Plan |
|---|---|---|
| Cloudflare Doctor | Trace-based diagnostic only | Port scanner stages incrementally; preserve explicit diagnostic-only label until real tunnel tests exist |
| Cloudflare account/panel, Worker deploy, usage and sync | Not implemented as account product | User-driven OAuth/token entry, secure storage, least privilege, deployment preview and revocation |
| Config Studio / templates / multi-location exits | Not implemented | Separate opt-in user-owned Worker project; no embedded account secrets |
| MLM Scout / route ranking | Endpoint preflight only | Add cancellable end-to-end tunnel probes, explainable ranking and failure evidence |
| Game Booster / game catalog / DNS and ping | Not implemented | Route measurement and per-app behavior require device testing and clear permissions |
| Smart Iran routing | Not implemented | Explicit policy engine; preserve default route and avoid unintended traffic capture |
| Per-app split tunneling | Not implemented | Android allowed/disallowed app policy through the single MLE VPN builder |
| Local proxy / hotspot / PAC sharing | Not implemented | Explicit local-network listener, consent, authentication and teardown |
| Subscription management / usage | Basic subscription fetcher | Add safe parsing, bounded downloads, expiry and usage only where source semantics are public |
| Backup / restore | Not yet audited in MLE baseline | Versioned export; encrypt secrets; never export raw account keys by default |
| Signed engine store / in-app updates | Not implemented | Signature verification, pinned trust policy, rollback protection and supply-chain review |
| Home board, folders, search, widgets, Widget Studio, Quick Settings tile | MLE home/catalog UI is smaller | Port selected interactions while retaining MLE design and accessibility |
| Crash reporting / analytics | Not ported | Do not copy upstream telemetry endpoints or enable telemetry without disclosure and consent |
| Gereh messenger | Not implemented | Separate security and licensing review; Rust E2EE core, Android bridge, relay protocol and Telegram-derived UI cannot be treated as a VPN engine |

## 4. Upstream source areas to audit

- `app/src/main/java/com/mlmvpn/core/`: Aether, Geph, Lantern, URnetwork, WARP, MASQUE and shared tunnel/service wrappers.
- `app/src/main/java/com/mlmvpn/scanner/engines/`: Amnezia, Arena, Cloudflare Doctor/network/cloud, FENIX, FLUX, Free Configs, Game Booster, Gateway, GitHub Tunnel, Geph, MAE, NPV, Outline, Proton, Scout, Telegram Proxy, VPN Gate, Windscribe and WireGuard modules.
- `app/src/main/java/com/mlmvpn/scanner/data/`: node/subscription/cloud/traffic/backup and Config Studio data managers.
- `app/src/main/java/com/mlmvpn/scanner/ui/`: home board, engine-specific screens, Cloud/Doctor/Studio, Game, Gereh import, LAN sharing, profile and settings pages.
- `app/src/main/java/com/mlmvpn/scanner/utils/` and `scanner/mitm/`: watchdogs, network policy, config generation, split tunnel and local certificate/fronting code.
- `mlm-chat/core/`, `mlm-chat/android/`, `mlm-chat/relay/`, `mlm-chat/docs/`, and `chat/`: separate Gereh messenger and Telegram-derived interface.
- `app/src/main/cpp/`: OpenVPN JNI and native tunnel bridge; inspect provenance and build scripts before porting any native component.

The upstream README identifies the product at version 1.2.45. Its public main source snapshot explicitly says private server addresses/keys were replaced by placeholders and prebuilt .so/.aar/.dat binaries were omitted. A placeholder is documentation, not a usable server.

## 5. Dependency and compatibility risks

1. **One VPN/TUN owner:** upstream engines cannot independently take over MLE's VPN service. Every adapter must acquire/release through the MLE arbiter and avoid closing another engine's descriptor.
2. **Native ABI and page size:** review arm64-v8a and armeabi-v7a support, NDK/CMake versions, 16-KiB ELF alignment, JNI symbol ownership and reproducible source hashes for every new core.
3. **Android lifecycle:** foreground-service policy, VPN consent/revoke, process death, network changes, battery restrictions and API-level behavior need instrumentation/device coverage.
4. **Protocol overlap:** avoid embedding duplicate Xray/sing-box/OpenVPN cores when an existing engine can safely implement the protocol. Keep unsupported options rejected.
5. **Private providers:** account tokens, Cloudflare API tokens, Worker URLs, relay identities and user configs must be supplied by the user, encrypted at rest and excluded from logs/backup by default.
6. **Native and binary provenance:** no opaque upstream APK, .so, .aar, .dat, release assets or downloaded binaries are to be copied. Build from reviewed public source at immutable revisions and verify hashes.
7. **Licenses:** upstream root license is GPLv3 with an additional attribution condition; each bundled dependency has its own license. Telegram-derived chat, Rust crypto and provider SDKs require separate review. Preserve notices and corresponding source obligations.
8. **Network/security claims:** TCP preflight, trace checks, a green UI state or APK packaging are not proof of tunnel connectivity, DNS integrity, IPv6 routing or leak prevention.
9. **Resource size:** upstream is a large repository (about 270 MB); avoid wholesale import. Port only audited source modules and their necessary dependencies.
10. **Sensitive source data:** even public files may contain placeholders, test fixtures or accidental secrets. Inspect candidate files for credentials and redact/exclude all such material before any transfer.

## 6. Staged migration plan and commit boundaries

1. **Audit baseline (this commit):** record repository heads, PR state, source inventory, target mappings, risks and test evidence. No app code is replaced.
2. **Shared architecture:** define adapter capabilities, connection states, diagnostics and a single-owner resource contract; add tests before new engines.
3. **Engines and libraries:** prioritize already-supported sing-box profiles and finish OpenVPN/WireGuard verification; then evaluate native/provider engines one at a time.
4. **Config/profile management:** typed import/export, subscription refresh, profile metadata, encrypted secrets and user-config onboarding.
5. **Tools:** Quick Connect, Scout, Cloudflare diagnostics/panel, split tunnel, Game Booster and local sharing only as real implementations.
6. **UI/assets:** engine-specific icons and screens mapped to stable IDs, retaining MLE palette, logo, Persian RTL and accessibility.
7. **Integration/release:** CI, unit/instrumentation tests, APK packaging, license audit and real-device acceptance matrix. Keep PR #10 Draft until the owner explicitly authorizes otherwise.

Each phase must be a separate commit on `integration/mlmvpn-foundation`. Do not merge PR #10 or modify `main`.

## 7. Verification snapshot at audit time

- Latest target branch head: `8466b743b89751d69ca752c6e832963a88046c48`.
- Latest Android CI on that head: [44929803964](https://github.com/mostafaazad369-web/Mle-vpn-/actions/runs/44929803964) — success.
- Latest Android build on that head: [44929804045](https://github.com/mostafaazad369-web/Mle-vpn-/actions/runs/44929804045) — success.
- Latest OpenVPN native build on that head: [44929803956](https://github.com/mostafaazad369-web/Mle-vpn-/actions/runs/44929803956) — success.
- These CI results establish the recorded workflow success only. They do not establish real-device VPN consent, live connection, authentication, IPv4/IPv6/DNS behavior, traffic accounting, reconnect, revoke or TUN cleanup.
- No physical Android device test has been performed by this audit.
- The upstream MAE/FLUX implementation-state documents report their own upstream tests/device observations; those are not MLE test results and must not be represented as such.

## 8. Immediate next engineering tasks

1. Read the MLE engine/service/profile/arbiter implementations and current tests before editing them.
2. Review upstream public source for one candidate module at a time; extract no credentials or server data.
3. Reconcile actual Gradle/native dependencies and immutable source locks against MLE's versions and licenses.
4. Add architecture contract tests and capability metadata; then wire one real engine at a time.
5. Keep every user-facing item accurately marked as available, diagnostic-only, account-required, or not yet implemented.
