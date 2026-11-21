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
2. **Engine adapters:** implement and test protocols already supported by sing-box first (including Shadowsocks, WireGuard and Hysteria2 only after confirming exact supported schemas). Add isolated native/provider adapters for OpenVPN, AmneziaWG, Psiphon, Tor, Geph, Lantern and URnetwork only after license/API/ABI review.
3. **UI:** add an MLM-inspired home board and per-engine cards/screens while retaining MLE colors, logo, RTL and accessibility. Each card's icon is unique and maps to its engine ID.
4. **Tools:** subscription and config import, Quick Connect, Scout, diagnostics, Cloudflare user-owned integrations, split tunneling and local sharing. Do not ship fake controls or hard-coded server credentials.
5. **Gereh:** audit `mlm-chat/` Rust core, Android package, relay service, `chat/` Telegram fork, Olm/Megolm dependencies and all licenses. Port behind a separate process/service boundary with encrypted key storage and a reviewed protocol.
6. **Release hardening:** native ABI builds, unit/instrumentation tests, real-device VPN switching, DNS/IPv6 leak checks, network changes, background lifecycle, crash handling, backup/restore and reproducible build verification.

## Engine catalog

The source code catalog is `app/src/main/java/com/mle/vpn/engine/catalog/MleEngineCatalog.kt`.
Every entry has a unique `id` and `iconKey`. Catalog presence is metadata only; it does not claim that an engine is integrated or connectable. Integration states are deliberately explicit.

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
