# MLM VPN compatibility and migration plan

This document separates public data sources from connection-engine implementations. Listing a feature here does not mean it is implemented or safe to advertise as connectable.

## Public config feeds identified in MLM VPN's Quick Connect source
- iampedii/whitedns-sub (primary + jsDelivr mirror)
- 0xRadikal/Free-v2ray-Configs: verified, fast, secure, and all feeds (primary + jsDelivr mirrors)
- Public Telegram channels are already maintained in `app/src/main/assets/telegram_sources.txt`.

The machine-readable registry is `app/src/main/assets/mlm_public_sources.txt`. MLE now loads this registry in the Sources screen, fetches each HTTPS feed with a mirror fallback, parses raw/Base64 payloads, filters profiles through `SingBoxOutboundBuilder`, and offers both per-feed and all-feed add-only refresh. Existing vault entries are preserved. A scheduled background refresh, persistent per-feed cache/TTL, and richer source health reporting remain future work.

## Deliberately not copied
MLM VPN's shared Quick Connect pool is not a public static feed. Its client enrolls each installation, signs requests, and receives network-scoped results from its own service. Do not copy its endpoint, signing scheme, server credentials, or private data. A separate MLE-owned backend or a documented public feed is required.

## Engine inventory
The engine catalog records the engine families described by MLM VPN: sing-box/Xray, WARP, Aether/MASQUE, Amnezia, OpenVPN, Outline, Psiphon, Tor, Geph, Lantern, URnetwork, Proton, Windscribe, FENIX, VPN Gate, NPV, FLUX, MAE, GST/EDG, GitHub Tunnel, Telegram Proxy, and SNI/ECH tooling.

The existing sing-box adapter remains the only runtime connected to MLE's VPN service. Standard single-peer WireGuard .conf files are now parsed, validated and emitted as sing-box 1.14 WireGuard endpoints; WARP .conf profiles can use this path when supplied by the user. Automatic WARP registration is not implemented. AmneziaWG-specific obfuscation parameters are rejected until its native Go/C runtime is integrated. Other engines remain inventory/planning only. Each additional engine requires a separate technical and license review, compatible runtime/ABI binaries where applicable, config handling, VPN service lifecycle integration, and real-device connectivity/leak tests. WireGuard imports currently support one [Peer] block only; multi-peer files are rejected instead of silently losing peers. Provider-owned services also require user authorization/account access. Do not show a catalog-only engine as functional.

## User profiles
User-imported profiles must remain in the encrypted local profile vault and must not be deleted by public-feed refreshes. Refresh should add new compatible profiles only, deduplicate by canonical endpoint, and report rejected/unsupported entries without logging secrets.
