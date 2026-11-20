# Native libbox initialization

`MleApplication` performs one-time initialization of the libbox native runtime:
- Creates private base/cache paths and an app-scoped working directory.
- Sets the native locale.
- Supplies `SetupOptions` with paths, app version, SDK metadata, and bounded log retention.
- Serializes initialization with a coroutine Mutex and avoids repeated setup calls.

This is runtime initialization only. It does not instantiate `CommandServer`, implement the complete `PlatformInterface`, open TUN, protect tunnel sockets, or forward packets. The UI must not report VPN-connected based on successful setup alone.
