# Implementation roadmap

## Phase 1 — bootstrap (this commit)
- [x] Clean Android/Compose project structure
- [x] Single VPN service boundary
- [x] Engine adapter contract
- [x] Strict URI recognition for common proxy schemes
- [x] CI build and unit-test workflow
- [ ] Verify Gradle build in GitHub Actions

## Phase 2 — real packet engine
- [ ] Pin a sing-box/libbox version and record source/license provenance
- [ ] Implement native platform callbacks and TUN descriptor lifecycle
- [ ] Protect upstream sockets and handle MTU/routes/DNS/IPv6
- [ ] Add integration tests on physical Android devices

## Phase 3 — profiles and chains
- [ ] VLESS/VMess/Trojan/Shadowsocks/SOCKS/WireGuard parsers
- [ ] TLS, REALITY, WebSocket, gRPC and XHTTP schema validation
- [ ] Encrypted profile vault backed by Android Keystore
- [ ] Explicitly supported chain graph with cycle detection

## Phase 4 — Aether-class capabilities
- [ ] WARP (WireGuard/MASQUE) adapter after protocol and licensing review
- [ ] Tor and Psiphon adapters with upstream provenance
- [ ] Smart endpoint probing and network-aware route selection
- [ ] Per-app split tunneling, kill switch, routing rules
- [ ] Local proxy sharing with authentication and LAN firewall
- [ ] Quick Settings tile, diagnostics and release signing

## Release gate
No feature is marked complete until CI passes, a physical-device test confirms real HTTPS connectivity, DNS/IPv6 leak tests pass, and disconnect/reconnect behavior is stable.
