# Architecture

## Runtime boundaries

1. Compose is presentation only; it never owns sockets or a TUN descriptor.
2. `MleVpnService` is the single Android VPN-service owner.
3. A concrete `TunnelEngine` validates configuration, starts the packet path and stops it deterministically.
4. Profile parsers convert untrusted links into typed profile data. The engine-specific config builder performs strict schema validation.
5. The app must distinguish engine started, TUN established, and internet verified. These are different states.

## Packet path target

`Apps → Android TUN → packet forwarder → local engine proxy → selected outbound/chain → Internet`

No connection action is enabled in this bootstrap commit. Creating a TUN without an active forwarder would silently drop traffic.

## Chaining rules

- One VPN service owns the Android TUN.
- Engine hops communicate through explicitly bound loopback interfaces or supported native chaining APIs.
- Upstream sockets must be protected from the VPN route to prevent loops.
- Every hop must expose startup failure and bounded shutdown.
- DNS and IPv6 policy must be applied at the packet-routing layer and verified with leak tests.

## Security

- Never log profile URIs, credentials, private keys, access tokens, or packet payloads.
- Reject unsupported protocols and transports instead of silently downgrading.
- Do not ship sample endpoints as working servers.
- Keep signing credentials out of source control; use GitHub Actions secrets.
