# Architecture

## Runtime components

- **Compose UI:** imports and selects profiles, requests Android VPN consent, starts/stops the service and observes explicit connection states.
- **MleApplication:** performs one-time native libbox setup in app-private storage.
- **MleVpnService:** the only Android `VpnService` owner. It supervises foreground lifecycle, selects the physical underlay, starts/stops libbox and closes the TUN descriptor.
- **MleBoxRuntime:** owns one sing-box `CommandServer`, validates JSON with the bundled core before startup and serializes reload/stop.
- **MlePlatformInterface:** implements libbox callbacks, creates Android TUN, configures routes/DNS and protects upstream sockets with `VpnService.protect(fd)`.
- **ProfileImporter / SingBoxOutboundBuilder:** parse untrusted share links and convert only explicitly supported protocol/transport variants.
- **ProfileVault:** stores credential-bearing profile URIs encrypted with AES-256-GCM and an Android Keystore key.

## Packet path

`Applications → Android TUN → sing-box/libbox routing and packet engine → selected outbound → remote server → Internet`

Unlike Aether's documented TUN-to-local-SOCKS bridge, this MLE implementation uses sing-box's native TUN inbound through libbox. Do not add a second VPN service or create a second TUN owner.

## Lifecycle and truthfulness

1. Obtain Android VPN consent before starting the foreground service.
2. Capture/select the physical underlay and protect engine sockets from the VPN route.
3. Validate config against the bundled libbox schema.
4. Start CommandServer and let its TUN inbound request Android's TUN descriptor.
5. Report tunnel-active only after startup completes.
6. Verify HTTPS traffic separately; never equate TUN creation with internet access.
7. On stop, close the engine service, descriptor and foreground notification deterministically.

## Security requirements

- Never log share URIs, credentials, private keys, access tokens or packet payloads.
- Reject unsupported protocols and transports; never silently drop TLS/REALITY parameters.
- Use HTTPS-only bounded subscription fetches.
- Keep signing keys outside source control.
- Validate DNS, IPv6, route behavior and network handover on physical devices before release.
