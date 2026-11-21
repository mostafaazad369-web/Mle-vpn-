# OpenVPN 3 port status for MLE VPN

**Status: dependency provenance imported; engine is not yet executable.**

This directory records the reviewed upstream OpenVPN 3 dependency lock. The original MLM VPN source is https://github.com/mlmvpn/mlmvpn_android and the MLE integration branch is `integration/mlmvpn-foundation`.

## Pinned dependency set

See [source-lock.json](../../third_party/openvpn3/source-lock.json). It pins:
- OpenVPN 3 at commit `2986b58108726a7aff278a50caa0cff9c4cdf943` (MPL-2.0)
- Mbed TLS 3.6.7 and mbedtls-framework at a fixed commit (Apache-2.0 OR GPL-2.0-or-later)
- Asio 1.30.2 (BSL-1.0)
- LZ4 1.10.0 (BSD-2-Clause)
- fmt 11.1.4 (MIT)

The source archives are not vendored in this commit. The lock's SHA-256 values must be checked against downloaded archives before extraction/build. Do not use floating branches/tags in place of the locked revisions.

## Why this is not an active engine yet

MLE currently has one VPN service/runtime path. A second engine must not start concurrently or silently replace an active tunnel. The upstream OpenVPN bridge expects callbacks for TUN creation, addresses, routes, DNS, socket protection, traffic statistics and core events. Those callbacks must be adapted to MLE's service ownership and cancellation model.

Required gates, in order:
1. Fetch each archive from the exact locked URL and verify SHA-256 before extraction.
2. Preserve the license files in `app/src/main/assets/openvpn-licenses/`; review notices for every component and the OpenVPN source's MPL file-level notices.
3. Port the JNI bridge behind an MLE-owned package/API. Audit JNI thread attachment, callback lifetime, exception handling, socket protection, route/DNS behavior and cancellation.
4. Implement an explicit single-owner VPN engine coordinator before registering another `VpnService`.
5. Add bounded profile import with strict directive allow-list, companion-file handling, certificate/peer validation and encrypted app-private credential storage. Never weaken TLS checks or accept scripts/plugins.
6. Build arm64-v8a and armeabi-v7a for API 24+ with 16-KiB ELF segment alignment; verify native symbols and package output.
7. Test parser/policy, permission grant/revoke, engine switching, process death, network changes, DNS and IPv6 leaks on physical devices.

No upstream TunnelBear sample profiles, CA certificates, accounts, credentials, or server endpoints are copied into MLE. A profile sample is not a service endpoint or a user-owned credential.

## Provenance

The lock and license notices in this branch are copied from MLM VPN's OpenVPN integration metadata. Upstream author credit: **mlmvpn** — https://github.com/mlmvpn. MLE's derivative remains subject to the repository's GPLv3 and applicable upstream attribution terms; OpenVPN and its dependencies retain their individual licenses.
