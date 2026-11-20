# Android VPN service foundation

MLE now declares a protected Android `VpnService` and contains a service lifecycle foundation.

## Safety boundary

The service intentionally does **not** establish a TUN device when it receives the start action. A TUN without a functioning packet engine would capture traffic and black-hole it. The current implementation therefore only posts a foreground notification saying the engine is awaiting initialization.

## TUN adapter contract

`MleVpnService.establishTun(TunConfig)` is reserved for the future sing-box `PlatformInterface.OpenTun` adapter. The adapter must:
1. Obtain user consent with `VpnService.prepare()`.
2. Start and validate the sing-box CommandServer and outbound configuration.
3. Ensure the packet engine is ready before calling `establishTun`.
4. Pass the returned file descriptor to libbox and keep its ownership/lifetime correct.
5. Call `protectTunnelSocket(fd)` for sockets used by the tunnel to prevent routing loops.
6. Close the engine and TUN resources on disconnect, revoke, or service destruction.

The service is not yet an operational VPN. No connection success should be shown until packet forwarding, DNS behavior, and end-to-end reachability are verified.

## Android platform requirements

The service is protected by `BIND_VPN_SERVICE`, advertises `android.net.VpnService`, and uses the `systemExempted` foreground-service type for eligible VPN apps targeting Android 14+. Google Play distribution still requires the corresponding policy declarations.
