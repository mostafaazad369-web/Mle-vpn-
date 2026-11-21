# WARP and AmneziaWG integration notes

## Cloudflare WARP profile support

MLE recognizes a standard WireGuard config as a Cloudflare WARP profile when its peer public key matches Cloudflare's published WARP peer key:

`bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=`

The profile is still a normal WireGuard endpoint config. It is converted to a sing-box WireGuard endpoint, and the app forces the sing-box data plane for that profile even if the user's global engine choice is Xray. This avoids passing a WireGuard profile into the Xray converter, which intentionally does not claim WireGuard support.

This feature requires a valid registered WARP profile supplied by the user. MLE does not currently create Cloudflare accounts, call undocumented WARP registration APIs, or provision WARP identities. The private key remains inside the existing encrypted profile vault.

## AmneziaWG safety and implementation boundary

AmneziaWG config fields are not equivalent to ordinary WireGuard fields. MLE detects `Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1-H4` and `I1-I5`, validates the complete file with the pinned official AmneziaWG config parser and sends the unchanged config to the isolated native AWG runtime. It must never drop these fields or try to connect with ordinary WireGuard.

The official Android project is Apache-2.0 licensed and contains a separate `:tunnel` Android library module. Its build invokes CMake and a Go userspace backend, with native outputs such as `libwg-go.so`, `libwg.so` and `libwg-quick.so`. The official backend exposes its own `GoBackend.VpnService`. MLE links the module, isolates the AWG host and backend service in `:awg`, and serializes engine start/stop so the AWG TUN is not run beside sing-box or Xray.

The runtime adapter is now selectable for imported AWG profiles, but it must remain marked adapter-in-progress until the following checks pass:
1. Fresh CI confirms native compilation, packaged ABIs and 16 KB ELF alignment.
2. Physical-device tests cover permission/revocation, startup failure cleanup, stop, process death and network handover.
3. Confirm handshake and sustained traffic, DNS, IPv4/IPv6 routes and leak protection, UDP and MTU behavior.
4. Verify the MLE orchestrator never starts a sing-box/Xray TUN alongside the AWG backend.

## Upstream references

- Official Android client: https://github.com/amnezia-vpn/amneziawg-android
- Official Android build instructions: https://github.com/amnezia-vpn/amneziawg-android#readme
- Official AmneziaWG protocol documentation: https://docs.amnezia.org/documentation/amnezia-wg/
- Official Android tunnel manifest: https://github.com/amnezia-vpn/amneziawg-android/blob/master/tunnel/src/main/AndroidManifest.xml

The official tunnel source is included as the pinned Apache-2.0 submodule; retain all upstream and nested submodule notices. No private WARP API implementation is copied into MLE.


## OpenVPN TLS client profile support

MLE accepts a strict subset of OpenVPN client files and maps them to the sing-box 1.14 `openvpn-client` endpoint. Supported fields include UDP/TCP remotes (up to 16), inline CA, optional paired client certificate/private key, inline `tls-auth` or `tls-crypt`, cipher/data-cipher preferences, minimum TLS version and selected keepalive/MTU settings.

External credential files, username/password prompts, scripts/plugins, compression, static-key mode, external certificate/key paths and unknown directives are rejected. The imported profile is stored using the existing encrypted profile vault. OpenVPN profiles always use the sing-box data plane even when Xray is selected globally.

This is endpoint-based OpenVPN client support, not a separate OpenVPN native engine. GitHub CI and real-device handshake, DNS, UDP, MTU and leak testing are still required before calling it verified.


## MLM Amnezia public WireGuard feeds

After adding a strict parser for standard wireguard:// and wg:// links, the silent public-source catalog also includes the public WireGuard/WARP lists used by MLM's Amnezia source registry:

- morpheusadam/v2ray-config — subs/bundles/wireguard.txt
- Argh73/VpnConfigCollector — Splitted-By-Protocol/WireGuard.txt
- Argh94/Proxy-List — WireGuard.txt
- Argh94/V2RayAutoConfig — configs/WireGuard.txt
- 10ium/VpnClashFaCollector — sub/all/wireguard.txt
- 10Dream/sub-mod — sub/split/protocols/wireguard.txt
- Delta-Kronecker/WARP-Config — ALL.txt

These are fetched only from public HTTPS GitHub/jsDelivr URLs and filtered through the sing-box endpoint builder before being saved. The parser accepts standard WireGuard keys, addresses, endpoint, allowed IPs, DNS, MTU, keepalive and three-byte WARP reserved values. Unknown query parameters and AmneziaWG-only noise/obfuscation options are rejected.

The FastNodes .conf directory listing and Delta-Kronecker AmneziaWG ZIP are not yet included: directory/ZIP source transport and an AmneziaWG native runtime are not integrated. This avoids silently treating an AWG profile as plain WireGuard.
