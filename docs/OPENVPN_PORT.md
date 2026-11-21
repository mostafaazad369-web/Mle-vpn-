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

## JNI ABI audit (2026-11-21)

The upstream bridge was compared directly with OpenVpnNative.kt and the callback methods in OpenVpnService.Session. The native entry-point contract is:

| Kotlin external method | JNI operation | Return |
|---|---|---|
| apiVersion() | apiVersion | int |
| evaluate(String) | evaluate | String |
| create() | create | long |
| run(long, String, String, String, Any) | run | String |
| stop(long) | stop | void |
| release(long) | release | void |

The bridge resolves these callback descriptors by name at runtime. The source-level signatures currently match the descriptors below; this audit corrects the earlier suspicion that the DNS callbacks had an arity mismatch:

| Callback method | JNI descriptor |
|---|---|
| configureTun(String, Int): Boolean | (Ljava/lang/String;I)Z |
| addAddress(String, Int): Boolean | (Ljava/lang/String;I)Z |
| addRoute(String, Int): Boolean | (Ljava/lang/String;I)Z |
| addDns(String, Int): Boolean | (Ljava/lang/String;I)Z |
| addSearchDomain(String, Int): Boolean | (Ljava/lang/String;I)Z |
| establishTun(): Int | ()I |
| onCoreEvent(String, Boolean): Unit | (Ljava/lang/String;Z)V |
| onCoreTraffic(Long, Long): Unit | (JJ)V |

Important semantics and porting constraints found during the audit:

- socket_protect(fd) is routed through configureTun("protect", fd); the MLE adapter must call the owning service's protectTunnelSocket(fd) and must fail closed if protection fails. The fd is a socket descriptor, not a TUN descriptor.
- The DNS callback's second integer is currently an ignored compatibility parameter in Kotlin; the bridge supplies zero. Do not mistake it for a DNS port. The bridge itself rejects pushed DNS ports other than 53.
- establishTun() returns a duplicated descriptor via ParcelFileDescriptor.dup(...).detachFd(), while the service retains and later closes its original descriptor. MLE must preserve this ownership split and close both sides exactly once.
- The upstream service configures a dynamic Android VpnService.Builder from server-pushed addresses, routes, DNS and search domains. MLE's existing MleVpnService.establishTun(TunConfig) is currently a sing-box-oriented fixed-config path; it is not a drop-in replacement for the OpenVPN callback builder.
- The upstream C++ client stores JNIEnv*, callback object and callback class for the duration of run. A port must verify which OpenVPN core callbacks can execute on worker threads. If any callback crosses threads, it must attach that thread to the JVM and use a global callback reference with deterministic release; a local reference and cached JNIEnv* must not be reused across threads.
- The upstream bridge clears Java exceptions in callbacks and converts many failures to generic codes. MLE should preserve cancellation and surface safe diagnostic codes without logging profile contents, inline certificates, credentials or private keys.
- The upstream bridge uses the package-mangled JNI symbol prefix Java_com_mlmvpn_scanner_openvpn_OpenVpnNative_. Moving the Kotlin object requires changing every exported symbol or adopting RegisterNatives; simply copying the .cpp file under a new package will leave native methods unresolved.
- The upstream configuration() currently enables asymmetric compression for compatibility with a specific provider. MLE must not inherit that provider-specific behavior globally. Compression remains rejected by MLE's conservative preflight unless a separately reviewed policy and security test justify an explicit exception.

The ABI comparison is source-level only: it does not prove that the upstream native binary matches the checked-in source, nor that the bridge builds against the pinned OpenVPN 3 revision. The next implementation step is a small MLE-owned JNI facade and callback interface, followed by native compilation and descriptor-level instrumentation tests before wiring it into the service. The engine arbiter is only an ownership guard; it does not yet provide OpenVPN packet processing or TUN construction.

## MLE JNI contract scaffold

The branch now contains `MleOpenVpnNative.kt`, an MLE-package JNI facade with the audited native entry-point signatures, optional library loading (`mleopenvpn`) and a kept callback base class. `MleOpenVpnNativeContractTest` uses JVM reflection to pin the entry-point and callback method descriptors. This is an ABI scaffold only: no `mleopenvpn` shared library, CMake target, OpenVPN source archive, runtime service adapter or connection path has been added. `available()` therefore remains false on builds without the native library, and the catalog must not present this as a working engine.

## Dynamic OpenVPN TUN groundwork

MLE now has an Android-independent `OpenVpnTunConfig.Builder` that collects server-pushed interface addresses, routes, DNS servers, search domains and MTU. It validates numeric IP literals and prefix lengths, normalizes IDN search domains, bounds collection sizes and requires at least one interface address and route before a TUN can be established. Unit tests cover IPv4/IPv6, default routes, reset semantics, malformed hostnames/prefixes and MTU bounds.

`MleVpnService.establishOpenVpnTun` translates that validated model into a dynamic Android `VpnService.Builder`. It retains the original `ParcelFileDescriptor` in the service and returns a detached duplicate fd to the future native callback, matching the audited upstream ownership contract. The retained descriptor is closed by the shared service cleanup paths. This is deliberately only a service primitive: no OpenVPN action, native worker, callback adapter or OPENVPN arbiter lease is wired yet. The existing sing-box start path remains the only active service engine, preventing a second TUN owner from being started accidentally.

## Shared-service callback adapter and IPv6 validator fix

The first dynamic-TUN CI run exposed a validator defect: the numeric-literal character allow-list rejected hexadecimal `a`-`f` characters in IPv6 addresses. The allow-list now accepts decimal digits, IPv4 punctuation and hexadecimal IPv6 digits while continuing to reject hostnames and non-literal input. The failing case was `fd00::2` in `OpenVpnTunConfigTest`.

An initial `MleOpenVpnServiceCallbacks` adapter now maps OpenVPN's `tun_builder_new`, MTU, address, route, DNS, search-domain, protect-socket and establish-TUN callbacks to the validated builder and the existing shared `MleVpnService`. Socket protection delegates to Android's `VpnService.protect(fd)` and fails closed. `establishTun` returns `-1` if validation or Android TUN establishment fails. Core events and traffic are forwarded through supplied callbacks.

This adapter is not yet passed to `MleOpenVpnNative.run`: the native library is absent, and lifecycle/lease integration still needs to be completed atomically. In particular, the future session must acquire `MleVpnEngine.OPENVPN` before calling native `run`, stop/join the native worker before releasing the lease, and close the service-retained TUN on every terminal path. JNI thread attachment/global-reference handling remains a required native bridge correction before production use.
