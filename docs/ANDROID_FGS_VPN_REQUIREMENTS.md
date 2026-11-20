# VPN foreground service requirements

The service is declared with the Android `systemExempted` foreground-service type because Android explicitly lists active VPN apps configured through the system VPN settings as an eligible use case. The matching `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` permission must remain in the manifest for Android 14+ targets.

The app must not start this foreground-service mode as a generic background task. The future connection flow must first obtain consent through `VpnService.prepare()`, initialize the VPN, and only then promote the active VPN service to foreground. Google Play's foreground-service declarations and policy review still apply.

Reference: https://developer.android.com/about/versions/14/changes/fgs-types-required
