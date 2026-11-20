# Third-party notices

## sing-box Android libbox
- Project: https://github.com/singbox-android/libbox
- Upstream core: https://github.com/SagerNet/sing-box
- Integrated Maven coordinate: `com.github.singbox-android:libbox:1.14.4`
- License: GNU General Public License v3.0 (GPL-3.0)
- Source and license: https://github.com/singbox-android/libbox

MLE VPN includes an Android adapter for the published libbox `PlatformInterface` and a `CommandServer` lifecycle wrapper. This implementation is an independent project and is not affiliated with or endorsed by SagerNet or the libbox maintainers.

Any APK distributing this dependency must comply with GPL-3.0, including providing the complete corresponding source and required license notices. Preserve the upstream copyright and license terms when redistributing libbox.


## Xray core via AndroidLibXrayLite
- Project: https://github.com/2dust/AndroidLibXrayLite
- Upstream core: https://github.com/XTLS/Xray-core
- Integrated release: `v26.11.9` (`libv2ray.aar`)
- Release asset SHA-256: `cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155`
- AndroidLibXrayLite license: GNU LGPL v3.0 (see [licenses/LGPL-3.0.txt](licenses/LGPL-3.0.txt))
- Xray-core license: Mozilla Public License 2.0; see upstream source distribution.

The build downloads the pinned AAR, verifies its SHA-256 digest, and applies the attributed, same-length gomobile namespace repackaging in [scripts/repackage-gomobile-aar.py](scripts/repackage-gomobile-aar.py) so its gomobile runtime does not collide with the existing sing-box AAR. The resulting AAR is generated under the Gradle build directory and is not committed. Python 3 is required for this build step.

The Xray adapter is selectable for VLESS, VMess and Trojan variants explicitly mapped by `XrayConfigBuilder`. Other imported protocols remain sing-box-only unless a matching Xray outbound is added and tested. Xray's Android TUN fd is passed to `CoreController.startLoop`; Android `VpnService` remains the sole TUN owner.

## MLM VPN build-tool attribution
- Source: https://github.com/mlmvpn/mlmvpn_android
- Adapted file: [scripts/repackage-gomobile-aar.py](scripts/repackage-gomobile-aar.py)
- License and required attribution terms: [licenses/MLM-GPL-3.0.txt](licenses/MLM-GPL-3.0.txt)

The AAR repackaging script is adapted from MLM VPN's GPL-3.0 build tooling. Original developer credit is preserved here and in the app's About/settings text. This project is independent and is not affiliated with MLM VPN.
