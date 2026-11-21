# Third-party notices

## sing-box Android libbox
- Project: https://github.com/singbox-android/libbox
- Upstream core: https://github.com/SagerNet/sing-box
- Integrated Maven coordinate: `com.github.singbox-android:libbox:1.14.4`
- License: GNU General Public License v3.0 (GPL-3.0)
- Source and license: https://github.com/singbox-android/libbox

## MLM VPN resources (selected snapshot)
- Project and authorship: MLM VPN / mlmvpn
- Source: https://github.com/mlmvpn/mlmvpn_android
- License: GNU General Public License v3.0 (GPL-3.0); preserve original copyright and license notices and provide corresponding source as required.
- Imported resource snapshot: `app/src/main/assets/mlm/`
- Resource inventory and limitations: `app/src/main/assets/mlm/RESOURCE_MANIFEST.json`
- The imported files are attributed upstream resources. They are not represented as operational services until MLE implements and validates their runtime integrations.
- Upstream README states that production server/relay addresses and keys are replaced with placeholders and prebuilt binaries (.so, .aar, .dat) are distributed only inside releases. Those private credentials and binaries are not recreated by this import.

MLE VPN includes an Android adapter for the published libbox PlatformInterface and a CommandServer lifecycle wrapper. This implementation is independent and is not affiliated with or endorsed by SagerNet, MLM VPN or their maintainers.

Any APK distributing GPL-covered components must comply with GPL-3.0, including providing complete corresponding source and required license notices. Review each imported asset's own provenance and license before redistribution.
