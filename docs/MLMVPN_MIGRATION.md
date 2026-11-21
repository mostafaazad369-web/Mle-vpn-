# MLM VPN → MLE VPN migration

**Status:** foundation branch; staged migration, not a completed port.
**Target:** MLE VPN Android, Kotlin / Jetpack Compose, existing sing-box 1.14.4 runtime.
**Upstream:** https://github.com/mlmvpn/mlmvpn_android
**Target:** https://github.com/mostafaazad369-web/Mle-vpn-

## Decisions recorded

- Work on `integration/mlmvpn-foundation`; do not change `main`.
- Preserve MLE's navy / turquoise / violet identity and existing launcher artwork.
- Keep the current Persian RTL UI and build each engine tile with its own stable icon key.
- Preserve upstream attribution to **mlmvpn** and GPLv3 notices.
- Gereh messenger is in scope, but is a separate subsystem and must be ported only after reviewing its Rust core, Android bridge, Telegram-derived UI and each bundled dependency's license.
- Never copy upstream redacted server addresses, private keys, account data, or assume placeholders are usable endpoints.

## Source inventory

The upstream README describes these connection methods and related systems:

- Protocols / tunnels: VLESS, VMess, Trojan, Shadowsocks, WireGuard, WARP, MASQUE, WARP-in-WARP, AmneziaWG, Hysteria2, OpenVPN, NPV, Outline.
- Provider / network clients: Psiphon, Tor, Geph, Lantern, URnetwork, Proton VPN, Windscribe, FENIX / Firefox VPN, VPN Gate.
- Adaptive and discovery systems: FLUX, MAE, Quick Connect, Iran profiles, SNI/ECH fronting, GitHub Tunnel, Telegram Proxy.
- Cloud and diagnostics: Cloudflare Panel, Cloudflare Doctor, Config Studio, MLM Scout, Game Booster, per-app split tunneling, local proxy / hotspot / PAC sharing.
- App shell: home board, folders/search, widgets, settings, signed engine store, backup/restore, in-app updates.
- Messenger: Gereh (Rust identity and Olm/Megolm E2EE core, Android package/bridge, Telegram Android-derived interface, relays and protocol docs).

See upstream README and CONTRIBUTING for the canonical evolving inventory and integration conventions.

## Current MLE baseline

MLE already has:
- Kotlin + Jetpack Compose, Persian RTL screens and its own navy/turquoise/violet theme.
- Encrypted profile storage using Android Keystore AES-GCM.
- VLESS / VMess / Trojan conversion for explicitly supported options.
- sing-box 1.14.4 libbox runtime, VPN permission flow, Android TUN setup and lifecycle wrapper.
- endpoint preflight checks, Telegram public-source import and tests.

These existing features should be extended rather than replaced. MLE documentation explicitly says unsupported outbound variants are rejected and that a successful runtime startup is not proof of working internet.

## Integration phases

1. **Foundation (this branch):** record upstream inventory, stable unique icon keys, compatibility boundaries, attribution and staged work.
2. **Engine adapters:** MLE already parses and converts VLESS, VMess, Trojan, Shadowsocks (legacy + SIP002), Hysteria2 and TUIC into sing-box outbounds; Shadowsocks plugins are rejected rather than silently ignored. These six are now marked as existing sing-box paths in the catalog. WireGuard still needs profile parsing, schema validation and runtime coverage. Add isolated native/provider adapters for OpenVPN, AmneziaWG, Psiphon, Tor, Geph, Lantern and URnetwork only after license/API/ABI review.
3. **UI:** add an MLM-inspired home board and per-engine cards/screens while retaining MLE colors, logo, RTL and accessibility. Each card's icon is unique and maps to its engine ID.
4. **Tools:** subscription and config import, Quick Connect, Scout, diagnostics, Cloudflare user-owned integrations, split tunneling and local sharing. Do not ship fake controls or hard-coded server credentials.
5. **Gereh:** audit `mlm-chat/` Rust core, Android package, relay service, `chat/` Telegram fork, Olm/Megolm dependencies and all licenses. Port behind a separate process/service boundary with encrypted key storage and a reviewed protocol.
6. **Release hardening:** native ABI builds, unit/instrumentation tests, real-device VPN switching, DNS/IPv6 leak checks, network changes, background lifecycle, crash handling, backup/restore and reproducible build verification.

## Engine catalog

The source code catalog is `app/src/main/java/com/mle/vpn/engine/catalog/MleEngineCatalog.kt`.
Every entry has a unique `id` and `iconKey`; the catalog now includes Gereh as a separately gated subsystem and TUIC as an existing sing-box protocol path. `EngineCatalogScreen.kt` exposes the inventory in the MLE UI with a distinct glyph per engine while preserving the existing MLE theme. A unit test checks unique IDs/icon keys, key requested entries and the six protocols already converted by the outbound builder.

The first executable adapter, `SingBoxEngineAdapter.kt`, bridges the existing MLE `EngineAdapter` contract to `MleVpnService`. It respects Android VPN consent and waits for the service's HTTPS-through-VPN verification before returning connection success. It does not add a new native engine; it formalizes the already-existing sing-box path. Other catalog entries remain metadata until their adapters are implemented and verified.

## Licensing and attribution checklist

- MLM VPN's root LICENSE is GPLv3 with an additional attribution term under GPLv3 §7. Keep the original license text and all upstream copyright notices.
- Credit **mlmvpn — https://github.com/mlmvpn** in the app's About / legal screen and in project documentation.
- Mark this derivative as modified and record modification dates.
- Review each bundled component independently. MLM VPN includes third-party projects with their own terms; upstream's overall GPL notice does not erase component-level notices.
- Gereh's Telegram-derived UI and cryptographic dependencies require a separate license inventory before copying.
- Preserve sing-box libbox GPL-3.0 notices already present in MLE.
- Before distributing APKs, provide the complete corresponding source and required build/install scripts for GPL-covered components.
- This document is an engineering checklist, not legal advice.

## References

- https://github.com/mlmvpn/mlmvpn_android
- https://github.com/mlmvpn/mlmvpn_android/blob/main/CONTRIBUTING.md
- https://github.com/mlmvpn/mlmvpn_android/blob/main/docs/OPENVPN.md
- https://github.com/mostafaazad369-web/Mle-vpn-
- https://github.com/SagerNet/sing-box
- https://github.com/singbox-android/libbox


## OpenVPN native-port audit (2026-11-21)

The upstream tree contains a real OpenVPN 3 integration, not just profile samples:

- Kotlin runtime: `OpenVpnNative.kt`, `OpenVpnService.kt`, profile import/runtime/repository/account policy and UI under `app/src/main/java/com/mlmvpn/scanner/openvpn/`.
- JNI/core adapter: `app/src/main/cpp/openvpn/bridge.cpp` and its CMake target. It wraps the pinned OpenVPN 3 client API and maps TUN addresses, routes, DNS, socket protection, traffic stats and lifecycle callbacks to Android.
- Native dependency lock: `third_party/openvpn3/source-lock.json` pins OpenVPN 3 commit `2986b58108726a7aff278a50caa0cff9c4cdf943`, Mbed TLS 3.6.7, Asio 1.30.2, LZ4 1.10.0 and fmt 11.1.4 with SHA-256 values and license identifiers. The core is MPL-2.0; dependencies retain their own licenses.
- Build: upstream `app/src/main/cpp/openvpn/CMakeLists.txt` and `scripts/build-openvpn.ps1` build arm64-v8a and armeabi-v7a for API 24 and 16-KiB page alignment. The upstream app packages prebuilt JNI libraries; the source tree inspected here does not contain the generated OpenVPN `.so` artifacts.
- Upstream `docs/OPENVPN.md` records profile import restrictions, certificate validation requirements, compression policy, account handling and real-device test requirements. Do not copy its sample TunnelBear profiles or CA into MLE as working endpoints/credentials.

MLE currently has no OpenVPN runtime files, no OpenVPN JNI libraries, and no OpenVPN-specific external-native-build wiring in `app/build.gradle.kts`. Its current Gradle build uses sing-box and a minimal Kotlin/Compose stack. A correct port therefore requires a deliberate native build/reproducibility decision plus a dedicated `VpnService` integration and mutual-exclusion handoff with MLE's existing service; copying only Kotlin files or catalog metadata would not produce a working OpenVPN client. The upstream native bridge also calls a specific callback contract and must be adapted and reviewed rather than assumed binary-compatible.

**Next OpenVPN implementation gate:** first import the pinned source/dependency lock and license notices (or produce reproducible, hash-verified ABI artifacts from those exact sources); then port/adapt the JNI callback contract and service lifecycle; then add safe profile import/storage and UI; finally run ABI packaging, parser tests, Android permission/handoff tests and real-device DNS/IPv6 leak checks. Until those gates pass, OpenVPN remains catalog-only and is not advertised as connectable.

## Latest CI verification

For head commit `5a8db1b0aed6010897f791f2ba0ac6d5f7e12c31`, GitHub Actions completed successfully on 2026-11-21: Android build `44900289968` (assemble-debug), Android CI `44900289965` (JVM/Android unit tests and debug APK packaging). This confirms the current foundation branch builds and packages; it does not validate native engines that have not yet been ported or live VPN behavior.


## OpenVPN dependency provenance imported into MLE (2026-11-21)

The next non-runtime migration step is now committed on the foundation branch:

- `third_party/openvpn3/source-lock.json`: exact upstream source URLs, immutable core/dependency revisions, SHA-256 values and license identifiers copied from MLM VPN.
- `third_party/openvpn3/README.md` and `docs/OPENVPN_PORT.md`: MLE-specific port gates, reproducibility and service-ownership constraints.
- `app/src/main/assets/openvpn-licenses/`: OpenVPN MPL-2.0, Mbed TLS, Asio, LZ4 and fmt license texts plus the source manifest.

No OpenVPN source archive has yet been vendored or built, no JNI/native library is packaged, and no OpenVPN service has been registered. The imported provenance/notice files are intentionally inert and do not change the app runtime. The next safe engineering step is hash-verified acquisition of the pinned sources and an isolated native build target; service integration must wait for a single-owner VPN coordinator and reviewed JNI lifecycle.

The immediately preceding foundation commit `d5857f0a65dd21be7bf6b35c3d05c18e7c11c6bf` passed Android build and Android CI (unit tests and debug APK packaging). New commits created by the provenance import trigger fresh workflows; their results must be checked separately.

## Gereh messenger source audit (2026-11-21)

A source-tree audit of the upstream `mlm-chat/` subtree confirms Gereh is a separate messenger product, not a VPN engine or a drop-in Android activity:

- The upstream tree contains 322 paths under `mlm-chat/`. Its Rust core (`mlm-chat/core/`) is declared Apache-2.0 in Cargo.toml and implements identity, Olm/Megolm sessions, encrypted envelopes/storage, groups, channels, Bluetooth/LAN/direct transports, Google mailbox and Nostr relay paths, plus a large protocol test suite.
- The wire contract is `mlm-chat/docs/SPEC.md` (draft 30 in the audited revision). The document explicitly describes itself as non-final; compatibility must be pinned to a reviewed spec revision and test vectors before interoperability is claimed.
- The Android integration is split into a Kotlin library (`mlm-chat/android/core/`, namespace `org.mlmchat.core`) and Rust JNI crate (`mlm-chat/android/jni/`, output `libmlmchat.so`). `android/scripts/build-native.sh` builds native artifacts into `core/src/main/jniLibs/`; those `.so` outputs are intentionally absent from Git.
- The core's Android build guide documents `arm64-v8a` as its currently supported default because the Telegram-derived UI native components are not built for all ABIs. The shell script comments/default ABI list are not perfectly consistent with that guide; MLE must resolve and verify the actual ABI matrix rather than copying assumptions.
- The UI is a separate Telegram Android fork under `chat/`, with a GPLv2 license file and many nested third-party modules/notices (including JLatexMath and media libraries). It must not be blindly imported into MLE's Compose app; each copied component needs provenance and license review.
- Upstream README states that relay addresses/keys are replaced with placeholders and release-only `.so`, `.aar` and `.dat` binaries are not in the public source tree. Do not restore, guess, or hard-code relay endpoints or credentials.

**MLE impact / implementation gate:** no Gereh Rust core, JNI bridge, Kotlin core module, relay implementation or Telegram fork has been integrated into MLE. The catalog tile is only an inventory entry. A safe port requires (1) freeze the protocol/spec revision and vectors, (2) vendor the Apache-2.0 Rust core and lockfile with a dependency/license inventory, (3) build and verify `libmlmchat.so` reproducibly for the ABI(s) actually supported by MLE, (4) adapt the Kotlin API and encrypted database-key storage to MLE's lifecycle, (5) decide whether to port selected Compose UI flows or the separate Telegram fork, and (6) test Bluetooth/LAN/direct/relay transports and E2EE interoperability. Gereh should remain disabled/non-connectable until these gates pass.

## CI verification update (2026-11-21)

The latest head commit checked for this update, `3260bd921915b2b0d04639443f63ae910ffaeba5`, passed both GitHub Actions workflows:
- Android build run `44900719343`: `assemble-debug` succeeded.
- Android CI run `44900719273`: JVM/Android unit tests and debug APK packaging succeeded.

These runs supersede the older CI run IDs recorded above. CI validates the current foundation branch only; it does not validate absent OpenVPN/Gereh native runtimes or real-device VPN behavior.
## NPV, WireGuard and Aether source audit (2026-11-21)

Further upstream inspection clarifies several similarly named catalog entries:

- **NPV is primarily a profile library/import workflow, not a new tunnel core.** `NpvLibrary.kt` distinguishes standard links (VLESS/VMess/Trojan/Shadowsocks/SOCKS/Hysteria2, run through MLM's existing Xray path) from native `.npvt` / `.npvs` files. The upstream code explicitly says those native files are stored and handed to the NPV Tunnel app; MLMVPN does not decrypt or run them because it has no authorized key. Remote Telegram posts and the NPV store are inventory/discovery features, not a protocol implementation.
- **WireGuard scanner is not equivalent to a general WireGuard client.** The inspected `WireguardScannerEngine` scans candidate IP/port endpoints and uses the existing Xray injector for tests; it is not itself a generic import/parser/runtime for arbitrary WireGuard profiles. The upstream `WireguardGuard` also documents a shared native Go runtime constraint with Xray actions.
- **Aether is not fully buildable from the public MLM repository alone.** `AETHER.md` says its Rust executable source lives in a separate private companion project; the public repository contains Kotlin process/service wrappers and a script for fetching release binaries, not the complete Rust source. Aether covers MASQUE / WireGuard / WARP-in-WARP, with a native `libaether.so` and `tun2proxy` TUN bridge. MLE cannot safely recreate or claim feature parity by copying only those Kotlin wrappers.

**MLE implementation consequence:** do not label the NPV catalog item as an independently implemented tunnel. A first safe NPV-compatible feature is to support importing the standard profile formats already handled by MLE's own converters and keep unsupported native `.npvt` / `.npvs` files explicitly unsupported. For WireGuard, implement and test a standard profile parser plus the exact pinned sing-box endpoint/outbound schema (or select a separately licensed runtime) before enabling the tile. Aether/MASQUE/WARP-in-WARP require source/runtime availability, ABI provenance and a single-owner VPN lifecycle; they remain unavailable until those requirements are met.
## WireGuard first implementation slice (2026-11-21)

MLE now contains a strict first-pass importer for single-peer wg-quick `.conf` profiles and a sing-box WireGuard **endpoint** JSON builder:

- `WireGuardConfigParser` validates Interface/Peer sections, 32-byte Base64 keys, endpoint host/port, CIDR addresses/AllowedIPs, keepalive and MTU.
- `PostUp`/`PostDown`, unknown directives, duplicate keys, malformed numeric fields and unsupported multi-peer profiles fail closed. This intentionally avoids silently discarding hooks or peer behavior.
- `ProfileImporter.parseWireGuardConfig` creates a sensitive `ImportedProfile`; its source text includes the private key and must only be stored in the encrypted profile vault.
- `WireGuardEndpointBuilder` emits the modern sing-box `endpoints[].type=wireguard` schema (sing-box 1.11+), rather than the deprecated WireGuard outbound removed in sing-box 1.13. `SingBoxConfigBuilder` now assembles this endpoint into a complete config and selects its tag as the final route and secure-DNS detour.
- Robolectric tests cover parsing, endpoint conversion, invalid keys/ports and fail-closed hook handling.

**Not yet a verified end-user VPN connection:** the parser and config assembly are implemented, but the importer is not yet wired into the profile-import UI/encrypted-vault UX, and no real-device WireGuard tunnel/kill-switch test has passed. DNS directives, multiple peers, and wg-quick hook semantics are deliberately unsupported in this first slice. Do not mark WireGuard as connectable until the exact bundled libbox runtime accepts the config and real-device tunnel/kill-switch tests pass.

Schema references used for this implementation: [sing-box WireGuard endpoint](https://sing-box.sagernet.org/configuration/endpoint/wireguard/), [Dial Fields](https://sing-box.sagernet.org/configuration/shared/dial/) and [WireGuard outbound migration](https://sing-box.sagernet.org/migration/#migrate-wireguard-outbound-to-endpoint). The endpoint was introduced in 1.11; the legacy outbound was deprecated in 1.11 and removed in 1.13. Since 1.14, endpoints with a domain peer address require `domain_resolver`; MLE explicitly sets it to the bootstrap DNS tag.

The MLE app dependency currently pins `com.github.singbox-android:libbox:1.14.4`; the WireGuard endpoint schema used here is documented as available since sing-box 1.11.0. The old WireGuard outbound is deprecated since 1.11 and removed in 1.13, so the implementation intentionally uses `endpoints` rather than an outbound object.
**Import UI wiring:** the existing profile dialog now recognizes a pasted wg-quick document (including leading comment lines), calls `parseWireGuardConfig`, validates it through `SingBoxConfigBuilder`, and saves the original sensitive text using the existing Android Keystore AES-GCM `ProfileVault`. The existing connect and export flows already consume `SingBoxConfigBuilder`, so the assembled endpoint JSON follows the same path as other saved profiles. This is still not evidence that libbox 1.14.4 accepts the endpoint config or that a live Android WireGuard session works; those remain CI/runtime/device gates.
## WireGuard CI update (2026-11-21)

- UI-import commit `f5093118b3c97bc58419f1e93db5ad5b23af32a2`: Android CI run `44907382833` passed unit tests and debug APK packaging; Android build run `44907382882` also passed.
- Strict single-peer/multiple-peer parser commit `c41e8bc977f5c29f9c3a12350623006fa2bf5b9d`: Android CI run `44907461593` passed the JVM/Android unit-test job, including the WireGuard parser/endpoint/config-assembly tests.
- The matching Android build run `44907461507` was still assembling the debug APK at the time of this update.

An earlier test attempt encountered transient Robolectric/Maven artifact-fetch 404s across unrelated existing tests; later test runs above completed successfully.