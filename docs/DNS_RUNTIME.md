# DNS Runtime — Phase 4

## Scope

This phase adds selectable encrypted DNS to the existing sing-box/libbox runtime. Android's existing `VpnService` remains the only TUN owner; no second VPN or DNS engine is introduced.

## Supported settings

- DoH (`https`) and DoT (`tls`) using sing-box 1.12+ DNS server formats.
- Provider presets: Cloudflare, Google Public DNS and Quad9.
- Custom encrypted DNS endpoint.
- For DoH, the numeric transport IP is paired with an explicit HTTP `Host` header and TLS SNI hostname.
- Numeric IPv4/IPv6 server address, independent TLS server name, port, and DoH path.
- Preferences are stored locally in app SharedPreferences.

## Strict bootstrap policy

- No `local` DNS server is configured.
- The bootstrap resolver is itself DoH/DoT and connects to a numeric IP literal, so resolving its own endpoint does not invoke Android/system DNS.
- Bootstrap queries used to resolve the proxy endpoint use the `direct` outbound over the physical underlay, protected by the existing Android platform socket-control callback.
- DNS queries originating from applications use the selected encrypted resolver through the `proxy` outbound.
- There is no plaintext UDP/TCP DNS server and no insecure fallback. If the configured encrypted resolver is unreachable, resolution fails closed.

The network can still observe the IP address of the encrypted DNS provider. Bootstrap DNS queries are encrypted but use the direct underlay because the proxy endpoint may itself require DNS resolution. This is a deliberate bootstrap trade-off, not a claim that all bootstrap metadata is hidden.

## Validation

- Local unit tests validate endpoint IP literals, TLS hostnames, ports and DoH paths.
- Config-builder tests assert DoH/DoT JSON structure, bootstrap/proxy detours and absence of a local resolver.
- Runtime configuration is validated by `Libbox.checkConfig` before CommandServer startup.
- CI tests and APK assembly do not prove DNS leak resistance on a physical device.

## Device evidence still required

1. Confirm DoH and DoT queries succeed through the selected profile.
2. Confirm a deliberately unreachable resolver causes DNS failure, not plaintext fallback.
3. Observe DNS requests at an authoritative test zone under project control, using unique query names.
4. Capture traffic on the underlay and verify no plaintext port 53/853 fallback is emitted by the app.
5. Test IPv4 and IPv6 separately, including network handover and resolver failure.
6. Record resolver-side logs and packet captures; do not mark leak protection as verified from UI state or a config string alone.

No VPN server is bundled or offered by this DNS feature. Public DNS provider presets are resolver endpoints only; users should review each provider's terms and privacy policy.
