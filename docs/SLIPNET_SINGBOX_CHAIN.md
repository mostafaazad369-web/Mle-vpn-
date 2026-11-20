# SlipNet + sing-box chain — implementation specification

## Can this guarantee access during a national-network shutdown?

No client can guarantee this. A chain only works if the current network still permits at least one required path and a reachable compatible server exists. If international routes, DNS, the required CDN, the tunnel endpoint, or the relevant transport are completely blocked, software on the phone cannot create connectivity from nothing.

The correct goal is a tested set of fallback transports and servers, with honest per-network results—not a promise that one fixed combination always works.

## Verified topology to implement

Use SlipNet as an upstream local proxy and sing-box as the only Android full-device VPN/TUN owner:

Android apps -> sing-box TUN -> SOCKS5 outbound (127.0.0.1:port) -> SlipNet tunnel -> compatible remote server -> Internet

SlipNet's official README documents that its CLI starts a local SOCKS5 proxy and supports DNSTT, NoizDNS, VayDNS, SSH, and other tunnel modes. Its README also documents DNS scanner and end-to-end tunnel testing. The local proxy is the integration seam; do not attempt to run two independent Android VPN services simultaneously.

## Why this topology

Android documents that only one VPN interface can be active for a user/profile at a time. Therefore MLE should own one VpnService/TUN through sing-box. SlipNet runs as a loopback-only upstream tunnel process and must not try to establish a second full-device TUN in chain mode.

## Required startup sequence

1. Validate and decrypt the user's SlipNet profile. Never hard-code public credentials or ship unknown third-party configs.
2. Start the SlipNet runtime with a loopback-only SOCKS5 listener on an available local port.
3. Wait for the listener; verify the process is alive and the SOCKS handshake succeeds.
4. Perform an end-to-end request through SlipNet's SOCKS proxy. A successful local TCP connect alone is insufficient.
5. Start sing-box/libbox with the Android TUN inbound and a SOCKS outbound targeting the loopback listener. The sing-box tunnel socket must be protected/bound to the underlying network so it does not loop back into its own TUN.
6. Verify a real HTTPS request through the full TUN, inspect the exit IP, and run DNS/IPv6 leak checks.
7. Only then report CONNECTED. If any stage fails, stop sing-box first, then SlipNet, and report the exact failing stage.

## Fallback and selection policy

Do not assume DNSTT, NoizDNS, VayDNS or Slipstream always wins. Test the available configured profiles on the current network. Prefer the fastest profile that passes end-to-end reachability and leak checks; prefer a more stable profile if latency is close. Apply bounded timeouts, cooldown failed profiles, and never endlessly retry one blocked endpoint.

Recommended profile order is network-dependent:
- DNS-only egress: DNSTT / NoizDNS / VayDNS, if the network's DNS path permits the configured tunnel domain and server.
- HTTPS-only egress: a configured NaiveProxy or TLS/WebSocket profile, if its endpoint is reachable.
- SSH reachable: direct SSH or SSH layered over a working DNS tunnel.
- Tor bridge mode: only when a configured bridge is reachable and the chosen SlipNet edition includes that mode.

A CDN hostname or SNI value is not a magic bypass. It works only with a correctly configured origin, valid domain/certificate/route, compatible server and provider terms. Do not generate fake CDN settings or public credentials.

## Important engineering checks

- Single TUN owner in chain mode.
- Loopback-only SOCKS listener; do not expose unauthenticated proxy on LAN.
- Protect tunnel sockets from VPN routing loops.
- Encrypted profile storage and redaction of passwords/private keys from logs.
- Process supervision, Android foreground-service lifecycle, boot/network-change handling.
- IPv4, IPv6, DNS and WebRTC leak checks.
- End-to-end HTTPS tests and sustained stability test, not ping-only selection.
- Stop both runtimes in reverse startup order on failure or revoke.

## Licensing / redistribution blocker

SlipNet's official README states AGPL-3.0 and describes the project as source-available with explicit restrictions/conditions around redistribution and republishing. Before copying its Android source, embedding its binaries, modifying it, or distributing an APK containing it, review the current license and obtain any additional permission needed from the copyright holder. Keep the integration behind the SlipNetRuntime interface until that review is complete. Do not silently copy its branding or UI.

## MLE code status

SlipNetSingBoxChain.kt currently defines the orchestration contract and failure-safe startup/shutdown order. It is not a functioning runtime adapter yet. The next implementation work is to build/package the permitted SlipNet Android runtime and sing-box/libbox runtime, implement the two runtime interfaces, add the VpnService foreground lifecycle, and run tests on actual mobile networks. No connection-success claim is valid before that work passes device tests.
