# sing-box outbound conversion scope

The first converter supports:
- VLESS: TCP, WebSocket, gRPC; TLS and Reality parameters when present.
- Trojan: TCP, WebSocket, gRPC with TLS.
- VMess: TCP, WebSocket, gRPC; TLS flag and standard UUID/security/alterId fields.

Other imported schemes are classified and stored but rejected by this converter until their URI formats and required fields are implemented and tested. This fail-closed behavior prevents silently dropping credentials, TLS, Reality, or transport parameters.

Limitations:
- Share-link compatibility varies by exporter; this is not a universal parser.
- Certificate validation is not disabled by default for VLESS.
- VMess TLS/host/path variants need validation against real exported links.
- This class does not start libbox or Android VpnService.
- Validate generated JSON against the exact pinned sing-box core before connecting.
