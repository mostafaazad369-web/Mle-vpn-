# Roadmap

## Implemented foundation
- [x] Kotlin / Jetpack Compose Android client
- [x] sing-box/libbox runtime initialization and CommandServer lifecycle
- [x] Android TUN PlatformInterface with route/DNS setup and socket protection
- [x] Profile import and encrypted Android Keystore vault
- [x] Outbound conversion for supported VLESS, VMess, Trojan, Shadowsocks, Hysteria2 and TUIC variants
- [x] HTTPS subscription fetch and endpoint probe foundations
- [x] CI unit-test and debug-APK workflow

## Next engineering work
- [ ] Confirm current CI build and unit tests are green
- [ ] Device-level smoke test: VPN consent, start, stop, reconnect and Android 14/15/16 FGS behavior
- [ ] Verify real HTTPS traffic and public exit IP through the selected profile
- [ ] DNS and IPv6 leak tests; ensure fail-closed routing on unsupported cases
- [ ] Underlying network handover and process-death recovery
- [ ] Review sing-box config schema against every advertised transport and add negative tests
- [ ] Improve state persistence and error diagnostics without exposing secrets

## Aether-class parity (separate adapters, not assumed complete)
- [ ] WARP via WireGuard/MASQUE after protocol and license review
- [ ] Tor and Psiphon adapters after upstream packaging/license review
- [ ] Explicit multi-hop chain graph with cycle detection, startup rollback and bounded shutdown
- [ ] Xray-core adapter for additional transports not supported by sing-box
- [ ] Per-app split tunneling, domain/IP routing rules and kill-switch policy
- [ ] LAN proxy sharing, Quick Settings tile and widget

## Release gate
No feature is marked production-ready until CI passes, physical-device HTTPS connectivity is verified, DNS/IPv6 leak tests pass, and reconnect/handover behavior is stable.
