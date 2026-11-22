# DNS tunnel + sing-box chain — implementation specification

## No guaranteed bypass

No client can guarantee connectivity during a nationwide shutdown. A chain works only while the access network permits at least one usable transport and a compatible server is reachable. If all routes to the required server are blocked, the phone cannot manufacture connectivity.

## Independent integration approach

MLE will not copy or redistribute the SlipNet application, its source, binaries, branding, or UI. Its current repository notice explicitly prohibits reuse of its code/name/logo and redistribution. Use independently licensed protocol implementations and standards-compatible components instead. This document refers to publicly described protocol concepts for interoperability research only.

## Topology

Android apps -> sing-box TUN (single Android VPN owner) -> local SOCKS5 outbound -> independently implemented DNS/SSH/HTTPS tunnel adapter -> compatible remote server -> Internet.

Android should have one active VpnService/TUN owner. The DNS tunnel adapter is a loopback-only upstream process; sing-box owns the device TUN.

## Startup and verification

1. Import a user-provided profile and validate it; do not hard-code public credentials.
2. Start the independent upstream tunnel bound to loopback only.
3. Wait for the SOCKS5 listener and verify the SOCKS handshake.
4. Test a real HTTPS request through the upstream proxy; a local TCP accept is not enough.
5. Start sing-box/libbox TUN with a SOCKS outbound to the local listener. Protect/bind the tunnel socket to the underlying network to avoid routing loops.
6. Verify end-to-end HTTPS through the full TUN, inspect exit IP, and check DNS and IPv6 leaks.
7. Report CONNECTED only after every check passes. On failure, stop sing-box first, then the upstream adapter.

## Fallback selection

Do not assume one DNS transport always works. Probe only configured profiles on the current network, with bounded timeouts and cooldowns. Select a profile only after a full end-to-end test. DNS tunneling is useful only if the network permits the relevant DNS path and a compatible DNS tunnel server is available. HTTPS/Naive-style and VLESS-over-WebSocket paths require correctly configured reachable origins, domains, certificates and servers. A CDN hostname or SNI value alone is not a bypass.

## Runtime requirements

- One Android TUN owner.
- Loopback-only local proxy.
- Protected underlying-network sockets to prevent TUN loops.
- Encrypted profile storage; redact credentials from logs.
- Foreground-service lifecycle, process supervision and reconnect handling.
- IPv4/IPv6/DNS leak checks and sustained traffic tests.
- Reverse-order shutdown on errors or permission revocation.

## Source and licensing review

The SlipNet repository currently displays a source-available notice that forbids copying/redistributing/publishing its app or code and forbids using its name/logo in another project. Therefore MLE must not bundle its APK, binaries, source or branding without explicit written permission. Any protocol implementation used instead must be independently sourced and have a license permitting MLE's intended distribution. Review each dependency and preserve its notices.

## MLE status

The generic `DnsTunnelSingBoxChain.kt` file defines the orchestration contract only. It is not a working tunnel runtime. Still required: an independently licensed tunnel implementation, sing-box/libbox Android runtime, VpnService integration, real profile parser, and device tests. No successful-connection claim is valid until those tests pass.
