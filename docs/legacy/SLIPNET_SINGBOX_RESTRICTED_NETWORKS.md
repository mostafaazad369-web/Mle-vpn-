# MLE VPN — SlipNet + sing-box integration and restricted-network strategy

## No unconditional connectivity guarantee

No VPN client can guarantee a connection on every national/intranet-only network. Success depends on which transports, DNS paths, IP ranges, SNI/QUIC patterns and remote endpoints the network still permits, and whether the user has a valid reachable server profile. MLE must never label a profile as working based only on a scan, ping, DNS response or a UI state.

## Licensing gate: SlipNet

The current official SlipNet repository states that its source is source-available for personal/private use and that redistribution of source or binaries, app-store publication and commercialization are not allowed without authorization. Therefore MLE must NOT copy SlipNet source, bundle its APK/runtime, rebrand it, or redistribute a derivative build unless the copyright holder first grants explicit written permission or publishes terms that permit the intended integration. Keep the adapter as a planned integration only until that gate is cleared. Preserve all required attribution and license notices for any separately permitted components.

Official project: https://github.com/anonvector/SlipNet

## sing-box / NekoBox

Use the upstream sing-box core through its supported Android integration route as MLE's first production engine, after reviewing the exact revision's license and dependency notices. NekoBox is an Android client built around sing-box; MLE should integrate the core/runtime API rather than copy NekoBox's UI or assume that NekoBox's whole Android app is a drop-in library.

Upstream project: https://github.com/MatsuriDayo/NekoBoxForAndroid

## Correct architecture: one Android VPN owner

Android's VpnService model means MLE should own one active TUN/VPN service and coordinate engine processes beneath it. Do not start SlipNet's Android VPN service and NekoBox's Android VPN service simultaneously and expect the tunnels to stack.

Potential chain, only if the exact runtimes expose compatible proxy interfaces and their routing behavior is verified:
1. Start an approved SlipNet transport process as a local SOCKS/HTTP proxy (not as a second device-wide VPN).
2. Configure sing-box to use that local proxy as its upstream dialer/outbound.
3. Let the sing-box TUN service be the only device-wide VPN interface.
4. Verify that the SlipNet process's own control traffic is not accidentally routed back into the sing-box TUN, which would create a routing loop.
5. Verify DNS behavior, MTU, UDP handling, IPv6, process death and reconnection.
6. If either side cannot expose or consume the required local proxy API, do not claim the chain exists; run the engines separately as mutually exclusive alternatives.

This is an architecture hypothesis, not an already implemented or proven feature. The SlipNet Android application and CLI have different integration surfaces; do not assume the CLI SOCKS mode is equivalent to the Android TUN mode.

## Restricted-network fallback policy

Implement a user-configurable, bounded connection plan rather than hard-coded claims:
- First test the currently selected user's valid profile and transport.
- Test DNS reachability separately from tunnel reachability.
- Try only transports for which the user has supplied a matching server/profile: e.g. DNS tunnel (DNSTT/NoizDNS/VayDNS), TLS/HTTPS proxy, VLESS WebSocket over TLS/CDN, SSH, or a conventional QUIC/UDP mode.
- Treat DoH as encrypted DNS only; it is not a full traffic tunnel by itself.
- Never invent public endpoints, keys, credentials, subscription URLs or CDN origins.
- Apply timeouts and a small retry budget; report why each attempt failed.
- Select a route only after the VPN interface is up and an HTTPS request, exit-IP check, DNS leak check and stability window succeed.
- Fall back to another tested profile; if all fail, report no usable route rather than displaying a fake Connected state.

## Diagnostics and test matrix

Record (without secrets): engine/version, profile type, transport, resolver mode, elapsed time, tunnel established, HTTPS probe, observed exit IP, DNS result, IPv6 leak result, MTU/packet loss, failure stage and timestamp.

Test on real devices and SIM/Wi-Fi networks with user authorization. Test normal internet, DNS-only access, UDP-blocked, QUIC-blocked, TCP/TLS-only and captive-portal conditions. National network filtering changes over time; tests from one operator/region/date cannot prove universal success.

## Implementation status

- [x] Engine catalog and common adapter contract exist.
- [x] This integration and safety/licensing plan is documented.
- [ ] Confirm written permission for SlipNet redistribution/integration.
- [ ] Integrate and test the supported sing-box Android core.
- [ ] Implement MLE-owned VpnService and lifecycle coordinator.
- [ ] Prototype local-proxy chaining behind a feature flag.
- [ ] Add routing-loop prevention and DNS/IPv6 leak tests.
- [ ] Test all supported transports against real reachable server profiles.
- [ ] Add automatic failover only after each candidate passes end-to-end checks.
