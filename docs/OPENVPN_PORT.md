# OpenVPN 3 port status for MLE VPN

**Status: dependency provenance imported; engine is not yet executable.**

Latest WireGuard integration and UI validation on the same branch also completed Android CI and debug APK assembly successfully: Android CI run `44907644370` and Android build run `44907644378` (commit `fcbaca36eac983e84a43c2402a1f2c2a5bf53d13`).

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

## Strict profile preflight (first Kotlin slice)

`app/src/main/java/com/mle/vpn/profile/OpenVpnProfilePreflight.kt` now performs a bounded, conservative preflight for pasted/imported OpenVPN text. It recognizes client/TUN mode, remote endpoints, transport and TLS peer-verification policy, accepts only a narrow directive allow-list, and returns non-secret metadata. It rejects unknown directives rather than silently dropping them, and explicitly blocks scripts, plugins, management interfaces, external credential/certificate paths and compression directives.

This is intentionally not yet the full OpenVPN importer: external companion-file selection, complete certificate/key validation, encrypted profile persistence, native runtime and VPN-service coordination are not implemented by this class. Its test suite covers allow-list enforcement, remote parsing, TLS verification requirements, compression rejection, credential-file rejection and size bounds. No profile can connect through OpenVPN until the native port and service integration gates above are complete.

The repository now includes `scripts/fetch-openvpn-sources.ps1` to materialize the six locked dependency archives for a native build host. It verifies SHA-256 before extraction, restricts downloads to GitHub codeload, rejects zip path traversal and writes provenance markers. This fetch step does not itself build or enable OpenVPN; the MLE JNI bridge, CMake target, ABI packaging and service adapter are still outstanding.

## CI checkpoint (2026-11-21)

- OpenVPN preflight validator and its tests at commit `c79827af49392402096f1cbf5dcba135fc08e23d` passed Android CI run `44907913535` (unit tests and debug APK package) and Android build run `44907913552` (debug APK assembly and artifact upload).
- The fetch-script and documentation commits are newer than that validator checkpoint; their CI is running separately. The fetch script itself has not been executed on a Windows/NDK host yet.
- This validates Kotlin compilation and tests only. No OpenVPN native library was built or packaged, and no OpenVPN connection is possible yet.