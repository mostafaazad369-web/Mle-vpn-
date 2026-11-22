# MLE VPN

An independent Android VPN client project inspired by the architecture and feature set of Aether Mobile. This repository is a clean rebuild, not a copy of Aether source code.

## Current status

This first rebuild commit establishes the Android/Compose application shell, a single VPN-service ownership boundary, engine contracts, profile-link parsing, CI and an explicit implementation roadmap. **It does not yet provide a working tunnel or claim that any server is connected.** A real packet engine must be integrated and verified before enabling connection controls.

## Architecture

- Jetpack Compose UI and application state
- One Android `VpnService` owner for TUN lifecycle
- Engine adapters behind `TunnelEngine`
- Profile import/parsing separated from engine configuration
- Secret storage and diagnostics designed as separate security boundaries
- CI build and unit-test workflow

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Build

Requires JDK 17 and Android SDK 35.

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## Security status

The app shell deliberately does not create a TUN device or report a successful VPN connection until a concrete engine adapter can own the packet path. Do not use this initial rebuild as a production VPN.

## License

MLE VPN is distributed under AGPL-3.0-or-later. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This project is independently implemented; Aether is referenced as an architectural study only.
