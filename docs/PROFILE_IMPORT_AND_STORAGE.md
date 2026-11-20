# Profile import and secure storage

- Supported share-link classifiers: VLESS, VMess, Trojan, Shadowsocks, Hysteria, Hysteria2, TUIC, AnyTLS, WireGuard, SOCKS and HTTP.
- The importer performs basic syntax/host/port checks only. It does not test connectivity, validate credentials, or guarantee all options can be translated to the selected core.
- Original profile URIs can contain UUIDs, passwords and private keys. They are stored encrypted using AES-256-GCM with a non-exportable Android Keystore key.
- Never log profile URIs, passwords, UUIDs, private keys, subscription URLs or decrypted JSON.
- Subscription fetching, format-specific conversion to sing-box JSON, and actual libbox start/stop are not yet implemented.
- CI builds a debug APK and uploads it as an artifact if Gradle dependency resolution and compilation succeed. A successful workflow is required before claiming a build.
