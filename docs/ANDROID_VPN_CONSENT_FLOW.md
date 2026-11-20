# Android VPN consent flow

The home action now calls `VpnService.prepare(context)` before any attempt to establish a VPN. If Android returns an Intent, the app launches the system consent screen using the Activity Result API. The result is recorded in UI state.

Important safety boundary:
- Approval means Android has authorized this app to request a VPN interface; it does not mean a tunnel exists.
- MLE does not start `MleVpnService`, establish a TUN, or change the device's network route from this consent-only flow.
- The UI explicitly reports that libbox is not yet connected and that traffic is not passing through MLE.
- A real connect action must only be enabled after the libbox CommandServer lifecycle, PlatformInterface.openTun implementation, socket protection, and packet engine startup are all wired and tested.

Android reference: https://developer.android.com/reference/android/net/VpnService#prepare(android.content.Context)
