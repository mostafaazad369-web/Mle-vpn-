# Supported profile import policy

The generic URI classifier recognizes a wider set of share-link schemes than the current sing-box outbound converter can build.

Before saving subscription results, the app now runs each parsed profile through `SingBoxOutboundBuilder.build`. Profiles that fail conversion are skipped instead of being presented as ready-to-use sing-box configurations.

Currently implemented outbound conversion is limited to supported VLESS, VMess, and Trojan variants. This is a configuration-conversion check only; it does not test server reachability, credentials, TLS handshake, DNS, or tunnel operation.

Additional protocols must receive a correct outbound converter and tests before being admitted by this preflight.
