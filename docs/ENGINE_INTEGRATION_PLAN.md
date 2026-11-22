# Packet engine integration plan

Aether's documented packet path is an Android TUN feeding a userspace
TUN-to-SOCKS forwarder, then a local SOCKS endpoint exposed by the selected
engine. MLE will implement an independently authored adapter architecture;
it will not copy Aether source or bundled binaries.

## Target data path

`Applications → Android VpnService/TUN → packet forwarder → 127.0.0.1 SOCKS → engine chain → upstream`

## Integration gates

1. **Engine runtime:** pin the upstream engine and ABI artifacts, verify provenance,
   license and redistribution obligations, then initialize it once per process.
2. **Platform interface:** implement TUN creation, route/DNS configuration,
   protect(fd), network binding and deterministic descriptor closure.
3. **Packet forwarding:** integrate a compatible TUN-to-SOCKS component, bind it
   only to loopback, and supervise it with the engine lifecycle.
4. **Session control:** use `TunnelSessionController` to serialize start/stop,
   surface failures, and distinguish tunnel-active from internet-verified.
5. **Verification:** run HTTPS reachability, DNS and IPv6 leak tests, reconnect,
   network handover, process-death and Android-version tests on physical devices.

## Aether-class engine roadmap

- Aether/WARP-compatible transport (WireGuard or MASQUE): separate adapter.
- Xray custom profiles: VLESS, VMess, Trojan, Shadowsocks and SOCKS.
- Tor and Psiphon: separately licensed and packaged adapters.
- Chaining: explicit directed graph with cycle detection and bounded startup,
  rollback and shutdown for every hop.

No adapter is considered implemented merely because it appears in the UI or
engine catalog. A release requires a passing end-to-end device test.
