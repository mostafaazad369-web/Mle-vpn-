# MLE VPN — Project Handover

## Current checkpoint

- Audit date: 2026-11-28
- Repository: https://github.com/mostafaazad369-web/Mle-vpn-
- Default branch: `main`
- Audited main HEAD: `65984ee029642a6121c7099218bb6bd1a0b1396c`
- Working branch: `review/phase-1-deep-audit`
- Working branch was created directly from the audited main HEAD. No application source files have been changed in this checkpoint.
- This document is the only intended file change for the Phase 1 audit checkpoint.
- No PR has been merged. Do not merge any PR without explicit user approval.

## Product goal

Continue the existing Android Kotlin application as a secure, real DNS + VPN client. Do not create a replacement project. Preserve the existing sing-box/libbox architecture and the single Android TUN ownership rule. A UI label, catalog entry, parser, or interface is not proof of a working transport. Never claim a connection until real traffic is verified. Do not bundle private credentials or use public relays without checking provenance, permission, and safety.

## Repository baseline

- Kotlin, Jetpack Compose, Material 3.
- Android Gradle Plugin 8.7.3; Kotlin 2.0.21.
- compileSdk 35, targetSdk 35, minSdk 24.
- CI installs Gradle 8.9 and JDK 17.
- sing-box Android libbox dependency: `com.github.singbox-android:libbox:1.14.4`.
- Root LICENSE text declares AGPL-3.0-or-later. GitHub API repository metadata currently reports SPDX `NOASSERTION`; reconcile metadata/licensing classification before release.
- The recursive main tree contains 95 entries. There is no Gradle wrapper (`gradlew` / `gradle/wrapper`) in the root tree; CI currently installs Gradle 8.9 directly.
- Main CI run `45128514301` on the audited HEAD completed successfully (unit tests + debug APK packaging). This is CI evidence only, not device-level VPN verification.

## Main architecture observed

- `MleApplication`: one-time libbox setup.
- `engine/MleVpnService.kt`: manifest-registered Android VPN service, foreground lifecycle, selected underlay, libbox lifecycle, traffic verification.
- `engine/MleBoxRuntime.kt`: owns a sing-box `CommandServer`, validates config with `Libbox.checkConfig`, serializes lifecycle.
- `engine/MlePlatformInterface.kt`: implements libbox platform callbacks, TUN creation, routes/DNS, socket protection, network/interface callbacks.
- `profile/SingBoxConfigBuilder.kt` and `SingBoxOutboundBuilder.kt`: generate sing-box configuration and supported outbounds.
- `profile/ProfileVault.kt`: AES-256-GCM encrypted profile storage using Android Keystore.
- `MainActivity.kt`: Compose UI, import flow, VPN consent flow, connect/disconnect and source scanning.
- The manifest registers `.engine.MleVpnService`. There is a second legacy class at `app/src/main/java/com/mle/vpn/vpn/MleVpnService.kt`, not referenced by the manifest; do not treat it as the active service.
- Architecture document says sing-box owns the native TUN inbound. Do not add a second VPN service/TUN owner.

## Key findings from Phase 1 deep audit

### High priority — fix or verify before claiming release readiness

1. **Service shutdown blocks in `onDestroy`.** The current `engine/MleVpnService.kt` uses `runBlocking(Dispatchers.IO)` inside `onDestroy`. Although the work is dispatched to IO, `runBlocking` still blocks the lifecycle caller thread. Review and redesign teardown to be deterministic and non-blocking without closing the TUN before the engine stops consuming it.
2. **Startup exception logging may expose profile data.** `MleVpnService` logs the full startup Throwable with `Log.e(..., error)`. Native/config validation errors may include configuration fragments. Replace with sanitized error categories/codes and ensure user-facing diagnostics do not include credentials, full share URIs, private keys or raw JSON.
3. **TUN application filters fail open silently.** `MlePlatformInterface.openTun` wraps `addAllowedApplication` and `addDisallowedApplication` in `runCatching { ... }` and ignores errors. For an explicitly requested split-tunnel policy this can silently apply a different policy. Propagate a typed failure or explicitly surface a degraded state; never silently ignore a security-relevant package rule.
4. **IPv4/IPv6 routing requires device verification.** The platform adapter has API-specific route handling and fallback behavior. Verify both address families, full default routes, exclusions, DNS routes and behavior when one family is unsupported. Do not infer leak prevention from the presence of TUN addresses/routes in code.
5. **Internet verification is limited.** `verifyInternetThroughVpn` checks HTTPS responses from a small endpoint list and accepts HTTP 200–399. It does not verify exit IP, DNS resolver path, IPv4/IPv6 leaks, or compare the pre/post-tunnel public IP. Captive-portal redirects may need to be treated separately from successful internet verification.

### Medium priority — architecture and correctness

6. **DNS chain is only an abstraction.** `DnsTunnelSingBoxChain.kt` defines interfaces and orchestration but no concrete DNS tunnel runtime. Its DNS leak signal is a magic string comparison (`full.detail == "DNS_LEAK_CHECK_PASSED"`). Implement typed, evidence-backed diagnostics before exposing DNS tunnel chain as a feature.
7. **Current sing-box DNS configuration needs leak tests.** The config uses a local bootstrap resolver and a DoH server (Cloudflare) detoured through the proxy. Validate the bootstrap behavior, hostname resolution and user-query path on physical devices; do not label it DNS-leak-proof without tests.
8. **Vault read failures are hidden from the UI.** `ProfileVault.loadAll()` converts decryption/parsing failures to an empty list. Mutations correctly use a strict reader to avoid overwriting unreadable data, but the UI may misinterpret a vault error as no saved profiles. Add a typed load result and recovery/export guidance without deleting ciphertext.
9. **Shutdown errors are swallowed by runtime.** `MleBoxRuntime.stop()` wraps `closeService()` and `close()` in `runCatching`, then returns normally. The caller can report disconnected even when native shutdown failed. Preserve error details in a sanitized typed result and retain ownership until stop succeeds.
10. **TUN ownership has redundant paths.** `MleVpnService.establishTun(TunConfig)` contains a separate Builder-based TUN path while the active libbox callback path is `MlePlatformInterface.openTun` → `holdTunDescriptor`. Confirm whether the former is dead code and consolidate/document one authoritative creation path.
11. **Legacy duplicate service class.** `app/src/main/java/com/mle/vpn/vpn/MleVpnService.kt` is a second `VpnService` subclass, not registered in the manifest. Check references and either document/deprecate or remove only after an approved cleanup; do not remove blindly.
12. **Documentation drift.** `docs/legacy/ANDROID_VPN_CONSENT_FLOW.md` describes a consent-only state and says the service/TUN is not implemented, which is stale relative to current main. `docs/ENGINE_INTEGRATION_PLAN.md` describes a TUN-to-SOCKS forwarder path, while `docs/ARCHITECTURE.md` documents the current native sing-box TUN path. Mark historical plans as superseded and maintain one canonical architecture description.
13. **UI organization.** `MainActivity.kt` is a large single file (~660 lines) combining state, connection control, source fetching and multiple screens. Refactor incrementally only after behavior tests exist.
14. **Profile conversion audit.** Import classification recognizes protocols beyond those currently converted. The UI currently filters unsupported builders, which is good; add negative tests for unsupported query parameters/transports and verify no security-relevant TLS/Reality parameters are silently discarded.

## Branches and open PR audit

At the time of this audit, the repository had 12 branches. All branch API entries, including `main`, reported `protected: false`; consider enabling branch protection/rulesets for `main` after the owner reviews repository policy.

Open PRs observed:
- #1 `feature/mlm-engine-upgrade → main`, Draft. Compare against current main is diverged (about 205 commits ahead / 23 behind). Adds Xray, large MLM resource snapshots, source catalogs, native submodule and many dependencies. Requires file-by-file security, provenance, dependency and license review. Not safe to merge as a single large change without reconciliation.
- #10 `integration/mlmvpn-foundation → backup/pre-rebuild-2026-11-23`, Draft. Important: its base is the backup branch, not main. Compare against current main is diverged (about 195 commits ahead / 20 behind). Adds OpenVPN 3 native/JNI integration, build scripts, source locks and broad code changes. Requires independent native build, provenance, license, ABI, 16 KiB page alignment and lifecycle review before integration.
- #3, #4, #7, #8 and #9 are stacked on feature branches (not directly on main). Review their parent chain and exact base commits before considering any merge. Their PR bodies explicitly state several device-level gates remain outstanding.
- Never merge a stacked PR or an old branch based on a backup branch directly into current main without rebasing/reconciling and reviewing the complete diff.

## Dependencies and build observations

- Dependency versions are explicit in `app/build.gradle.kts`; there is no version catalog.
- Repositories: Google, Maven Central and JitPack.
- libbox is a native/core dependency. Verify exact artifact provenance, checksums/ABI contents, GPL-3.0 corresponding-source obligations and redistribution notices before release.
- `THIRD_PARTY_NOTICES.md` records libbox as GPL-3.0 and AndroidX/Compose/Kotlin as Apache-2.0.
- No local Gradle execution was performed during this remote source audit. The successful main CI run is the only fresh build/test evidence collected here.
- No physical Android device was available for VPN, DNS, IPv6, handover, reconnect or kill-switch tests.

## Phase 1 status

- [x] Revalidated correct repository and main HEAD.
- [x] Created isolated branch `review/phase-1-deep-audit` from audited main HEAD.
- [x] Reviewed architecture, app module build files, manifest, main service/runtime/platform/DNS chain, config builders, profile importer/vault, CI, docs, root tree and open PR metadata.
- [x] Identified priority risks and branch divergence.
- [ ] Commit this handover document and open a Draft PR (only on the review branch).
- [ ] Obtain user approval before implementing any code fixes or moving to Phase 2.

## Next recommended work (after explicit user approval)

Start with a small hardening PR on a new implementation branch based on current main (or continue the approved review branch if the user requests it):
1. Add tests for lifecycle stop/start races, sanitized errors and app-filter failure handling.
2. Redesign service teardown and make runtime stop failures observable.
3. Introduce typed diagnostics for DNS and full-tunnel verification; do not claim leak prevention before device tests.
4. Add typed vault load errors and recovery behavior.
5. Remove documentation drift and clarify the one active TUN owner.
6. Run CI and request physical-device validation before declaring production readiness.

## Rules for the next assistant

- Re-fetch current repository state before acting; this document can become stale.
- Keep `main` untouched. Work on an independent branch and use Draft PRs for review.
- No auto-merge and no merge without explicit user approval.
- Do not delete files, add dependencies, or change engine architecture without inspecting references and license implications.
- Work on only the user-approved phase; stop and ask before starting the next phase.
- At the end of every phase, update this file with exact branch/commit, changed files, tests, build results, remaining risks, and the next-step handover prompt.
- Always provide the user a complete Persian section titled **پرامپت انتقال پروژه به چت بعدی**.
