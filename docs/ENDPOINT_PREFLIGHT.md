# Endpoint preflight diagnostics

MLE VPN can run a bounded TCP reachability check for each saved profile from the profile list.

## What it checks
- Resolves the configured host through the device's normal DNS resolver.
- Attempts a TCP connection to the profile host and port.
- Uses a four-second connection timeout.
- Reports a coarse latency measurement when the TCP connection succeeds.

## What it does not check
- TLS certificate validation or SNI.
- VLESS/VMess/Trojan authentication.
- WebSocket, gRPC, Reality, or other transport negotiation.
- Whether the endpoint is reachable through the eventual VPN tunnel.
- End-to-end internet access after a tunnel is established.

The result is intentionally labelled as a TCP port preflight. A reachable port is not a working proxy and must never be presented as a successful VPN connection.
