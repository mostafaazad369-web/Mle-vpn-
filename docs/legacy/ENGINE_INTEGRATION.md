# MLE VPN — multi-engine integration plan

## Important distinction

The engine catalog in the app is a roadmap, not a claim that every listed engine is already bundled. An engine becomes production-ready only after its permitted runtime/core is packaged, its Android VPN lifecycle is integrated, and real-device connection tests pass.

## Engine families

### NekoBox / sing-box foundation
Use the sing-box Android/libbox integration as the first broad protocol adapter. NekoBox documents support for VLESS, VMess, Trojan, Shadowsocks, ShadowsocksR, Hysteria 2, TUIC, WireGuard, AmneziaWG, AnyTLS, ShadowTLS, NaiveProxy, SSH, SOCKS/HTTP and more. Keep MLE's UI and profile management original; do not copy NekoBox branding or UI.

### SlipNet
SlipNet is a separate engine family, not merely another sing-box profile. Its documented tunnel types include DNSTT, NoizDNS, VayDNS, Slipstream, SSH, NaiveProxy, DoH and Tor bridges, with optional SSH chaining. These modes generally require a compatible server/profile. Integrate only through its permitted source/runtime and preserve its license notices.

### MLM VPN feature-parity targets
- Xray: VLESS, VMess, Trojan, Shadowsocks; REALITY, TLS, WebSocket, gRPC and XHTTP transports.
- Cloudflare WARP and WARP-in-WARP.
- Aether: MASQUE and WireGuard-based paths.
- AmneziaWG, WireGuard and Hysteria2.
- OpenVPN, Outline, VPN Gate / SoftEther and NPV import.
- Psiphon, Tor, Geph, Lantern and URnetwork.
- Optional account-based providers: Proton VPN, Windscribe and FENIX.
- Supporting tools: subscriptions, batch tests, DNS checks, SNI/ECH diagnostics and per-app routing.

## Runtime architecture

Android permits only one active VpnService owner for the device tunnel at a time. MLE must therefore use one lifecycle coordinator and start one selected engine at a time. Proxy chaining is possible only where the involved engines expose compatible local proxy interfaces; it must not start two independent full-device VPN services and assume they can stack.

Suggested layers:
1. EngineCatalog: user-facing engine metadata.
2. EngineAdapter: common prepare/connect/disconnect/health contract.
3. VpnLifecycleCoordinator: owns Android VPN permission, foreground service and active-engine exclusivity.
4. ProfileRepository: encrypted local storage for profiles and subscriptions.
5. ConnectivityProbe: tests DNS, TCP/HTTPS reachability, tunnel state, exit IP and latency.
6. AutoSelector: ranks only engines that have actually passed the end-to-end probe on the current network.

## Success criteria

A ping response alone is not proof of a working VPN. A profile is healthy only when:
- Android reports the VPN interface established;
- a test request succeeds through the tunnel;
- the observed exit address matches the selected route;
- DNS behavior and IPv4/IPv6 leakage are checked;
- the session remains healthy for a stability window.

Never show Connected from a mock state or a successful socket ping alone.

## License and attribution review

- MLM VPN source is GPL-3.0 with an additional attribution requirement. Any derivative use must retain GPL obligations and clearly credit mlmvpn in the app, README and distribution materials.
- NekoBox for Android is GPL-3.0. Review the exact upstream revision and all bundled dependency notices before redistribution.
- SlipNet is AGPL-3.0. Network-use and redistribution obligations must be reviewed before integrating its code or binaries. Do not copy source files or ship its runtime until the license and notices are included.
- Each provider's SDK, client binary, API and account terms must be checked separately.

## Current implementation state

- [x] Branded Compose UI and launcher vector icon.
- [x] Engine catalog and adapter contract.
- [ ] sing-box/libbox Android runtime.
- [ ] SlipNet native runtime and profile parser.
- [ ] Xray runtime.
- [ ] Android VPN foreground service and lifecycle coordinator.
- [ ] Encrypted profile storage and subscription parser.
- [ ] Real-device connectivity and leak tests.
- [ ] CI build and signed release APK.
