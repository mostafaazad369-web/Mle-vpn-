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
- Xray-core license: Mozilla Public License 2.0 (see [licenses/MPL-2.0-Xray-core.txt](licenses/MPL-2.0-Xray-core.txt)).

The build downloads the pinned AAR and verifies its SHA-256 digest. The AAR is generated under the Gradle build directory and is not committed.

The Xray adapter is selectable for VLESS, VMess and Trojan variants explicitly mapped by `XrayConfigBuilder`. Other imported protocols remain sing-box-only unless a matching Xray outbound is added and tested. Xray's Android TUN fd is passed to `CoreController.startLoop`; Android `VpnService` remains the sole TUN owner.



## Xray gomobile runtime namespace isolation
- Adapted build tool: [scripts/repackage-gomobile-aar.py](scripts/repackage-gomobile-aar.py)
- Original source: https://github.com/mlmvpn/mlmvpn_android/blob/main/scripts/repackage-psiphon-aar.py
- License and attribution terms: [licenses/MLM-GPL-3.0.txt](licenses/MLM-GPL-3.0.txt)

The build tool is adapted from MLM VPN's GPL-3.0 script and preserves its attribution. It isolates the Xray gomobile Java/JNI runtime namespace from sing-box and handles both SysV-hash and GNU-hash-only ELF builds. MLE VPN remains an independent project and is not affiliated with MLM VPN.

## MLM VPN source and selected assets
- Project: MLM VPN / mlmvpn
- Source: https://github.com/mlmvpn/mlmvpn_android
- License: GNU General Public License version 3.0; exact upstream license copy: [licenses/MLM-VPN-GPL-3.0.txt](licenses/MLM-VPN-GPL-3.0.txt)
- Selected attributed assets: `app/src/main/assets/mlm/`
- Inventory, provenance notes and intentionally excluded sensitive assets: [RESOURCE_MANIFEST.json](app/src/main/assets/mlm/RESOURCE_MANIFEST.json)
- Imported Cloudflare Worker files are source-only and require deployment and operator-owned secrets. No private keys or credentials are copied.
- The upstream Amnezia seed containing WireGuard private keys, credential-bearing seed configs and authentication-sensitive Worker modules are intentionally excluded.
- The imported assets are not represented as live or functional VPN services until the corresponding engine, server infrastructure and device tests are complete.

Preserve upstream copyright, GPL-3.0 and applicable third-party license notices when modifying or distributing these assets. MLE VPN is not affiliated with or endorsed by MLM VPN.


## MLE MAE integration note
The app parses the attributed MLM MAE service registry as read-only hints and offers a user-controlled per-app allow-list. This does not copy or claim the full upstream adaptive routing engine. Package routing is implemented through sing-box TUN `include_package` and Android `VpnService.Builder.addAllowedApplication` for Xray. The MLE app itself is kept inside the allow-list so end-to-end tunnel verification remains meaningful.
