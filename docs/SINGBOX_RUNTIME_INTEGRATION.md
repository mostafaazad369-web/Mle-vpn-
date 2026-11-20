# sing-box Android runtime integration

## Components

- `MleApplication` initializes the native libbox runtime once.
- `MleBoxRuntime` owns the `CommandServer` start/stop lifecycle and serializes lifecycle changes.
- `MlePlatformInterface` implements the Android-facing libbox callbacks.
- `MleVpnService` requests foreground service execution, retains the Android TUN descriptor, handles revocation and shutdown.
- `MainActivity` passes the selected saved profile as generated sing-box JSON only after Android VPN consent.

## Connection flow

1. The user taps Connect.
2. The app calls `VpnService.prepare()` and waits for the system consent result.
3. The app builds a sing-box configuration from the selected saved profile and starts `MleVpnService`.
4. The service enters foreground mode and starts `MleBoxRuntime`.
5. `MleBoxRuntime` ensures libbox setup, creates and starts `CommandServer`, then submits the JSON configuration.
6. sing-box calls `MlePlatformInterface.openTun`; the adapter configures Android addresses, DNS, route prefixes, excluded routes where supported, and per-app filters.
7. The returned file descriptor is retained by the VPN service for the lifetime of the native engine. Outbound sockets are protected through `VpnService.protect(fd)`.
8. Stop, service revocation, or a startup error closes the command server and TUN descriptor.

## Important implementation boundaries

- This is the first runtime integration pass, not a verified production release.
- The outbound converter currently handles only its explicitly supported VLESS, VMess and Trojan variants. Unsupported transport/security combinations are rejected.
- Optional root-only platform features, SSH agent, shell, bridge, and auto-redirect are intentionally unsupported.
- The network monitor and process-owner APIs still need device-level testing across Android versions.
- No claim is made that any profile works on restricted/mobile national-network conditions; this depends on the endpoint, transport, DNS, carrier and routing environment.
- A successful command-server start is not proof of internet reachability. End-to-end traffic and leak tests are still required.

## References

- Android VPN guide: https://developer.android.com/develop/connectivity/vpn
- Android VpnService API: https://developer.android.com/reference/android/net/VpnService
- libbox Android library: https://github.com/singbox-android/libbox
- sing-box source/API: https://github.com/SagerNet/sing-box
