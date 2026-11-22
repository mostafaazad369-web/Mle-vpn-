# VPN service lifecycle and TUN ownership

## Current implementation boundary

`MleVpnService` is a service foundation, not a complete VPN engine adapter. Its START action only promotes the service to foreground and deliberately does not call `Builder.establish()`. This avoids routing all device traffic into a TUN descriptor that has no packet pump.

## Required connection sequence

1. The UI calls `VpnService.prepare()` and waits for explicit user approval.
2. Start the VPN service only after approval.
3. Initialize the selected sing-box configuration and create the libbox command server.
4. Ensure the outbound transport sockets are protected with `VpnService.protect(fd)` before connecting them, to prevent routing loops.
5. Implement the exact libbox `PlatformInterface.openTun` callback and call `Builder.establish()` only when the packet engine is ready to read and write packets.
6. Transfer the detached file descriptor to the native engine. The native engine then owns that descriptor and must close it when the engine stops.
7. Stop the command server and packet engine before closing any remaining Java-owned descriptors.
8. Handle `onRevoke()`, service destruction, failed startup, and user stop idempotently.

## Descriptor ownership

`ParcelFileDescriptor.detachFd()` transfers ownership of the raw descriptor to the native side. Once detached, the Java `ParcelFileDescriptor` no longer owns it. The component receiving the integer descriptor must close it exactly once. The service must not assume it can close a descriptor after successful detachment.

## Current limitations

- No libbox `CommandServer` is created yet.
- No generated libbox `PlatformInterface` implementation is registered.
- `establishTun()` is a prepared helper and is not called by the START action.
- No packet forwarding, tunnel handshake, connection health, or live server test is implemented.

## Android references

- https://developer.android.com/develop/connectivity/vpn
- https://developer.android.com/reference/android/net/VpnService
- https://developer.android.com/reference/android/net/VpnService.Builder#establish()
