# MLE VPN — Phase 2 Hardening Handover

## Repository checkpoint

- Repository: https://github.com/mostafaazad369-web/Mle-vpn-
- Main baseline: `65984ee029642a6121c7099218bb6bd1a0b1396c`
- Working branch: `hardening/phase-2-lifecycle`
- This branch was created directly from the main baseline above.
- Previous audit branch/PR remains separate: `review/phase-1-deep-audit`, Draft PR #11.
- No changes have been made to `main`; no PR has been merged.

## Phase 2 scope

User approved a bounded hardening pass covering VPN lifecycle/shutdown, safe diagnostics, fail-closed Android per-app routing rules, and typed profile-vault read errors. This is not a DNS feature phase and does not establish production readiness.

## Changes made on this branch

1. `MleVpnService.kt`
   - Removed synchronous `runBlocking` from `onDestroy()`.
   - Unified ACTION_STOP, libbox stop callback, revoke, startup-failure cleanup and onDestroy through an idempotent asynchronous shutdown scheduler.
   - Stops libbox before closing the retained TUN descriptor.
   - Does not close the TUN descriptor if libbox shutdown fails; keeps the foreground service and allows another stop attempt instead of falsely reporting disconnected.
   - Reports sanitized failure categories and does not log raw Throwable/stack trace.
2. `MleBoxRuntime.kt`
   - Stop now attempts both `closeService()` and `close()`, preserves suppressed cleanup failures and propagates shutdown failure.
   - Keeps CommandServer ownership when shutdown is not confirmed, allowing retry.
   - Startup cleanup failures are attached as suppressed exceptions rather than silently discarded.
3. `MlePlatformInterface.kt` and new `ApplicationRuleApplier.kt`
   - Removed silent `runCatching` around Android allow-list/deny-list calls.
   - Any failed package rule now aborts TUN establishment with a safe typed exception.
4. New `MleVpnFailure.kt`
   - Maps exception classes to safe Persian diagnostics.
   - Log category includes only exception type, never raw exception text.
5. `MainActivity.kt`
   - VPN startup/config-building failures shown to the user use sanitized messages.
   - UI reads typed vault result and shows a warning instead of presenting an unreadable vault as genuinely empty.
   - Delete failure is surfaced without overwriting stored ciphertext.
6. `ProfileVault.kt`
   - Added `ProfileVaultLoadResult` and `loadAllResult()`.
   - Unreadable/corrupt encrypted data is reported as unavailable; ciphertext is left intact.
   - Legacy `loadAll()` remains as a compatibility accessor and still returns an empty list on failure. New UI code must use `loadAllResult()`.
7. Tests added:
   - `ApplicationRuleApplierTest.kt`
   - `MleVpnFailureTest.kt`
   - `ProfileVaultLoadResultTest.kt`

## Important limitations / review items

- No local Gradle execution was performed by the GitHub connector. Wait for branch CI and inspect the actual workflow logs.
- No physical Android device was available. Lifecycle races, Android foreground-service behavior, revocation, actual TUN descriptor ownership, reconnect, DNS leak, IPv4/IPv6 leak and network handover remain unverified on devices.
- The shutdown design intentionally retains the foreground service and TUN descriptor if libbox cannot confirm shutdown. Validate retry behavior on Android 8–15 and inspect whether service callbacks can re-enter while a shutdown attempt is active.
- `onDestroy()` is now non-blocking, but Android may terminate the process before asynchronous cleanup finishes under hard process death; no in-process design can guarantee cleanup after process kill.
- The service still has a possible rapid START/STOP sequencing race that needs dedicated lifecycle tests and device validation.
- ProfileVault's old `loadAll()` compatibility method still hides errors. Search all call sites and migrate them before removing it in a later approved change.
- `MleBoxRuntime.stop()` calls `discardPowerReportDraft()` after native close; verify this does not throw or mask cleanup state.
- Per-app filtering is fail-closed only for rules supplied to the platform callback; verify behavior with real package names and API levels.
- No DNS chain implementation, IPv6 leak prevention, kill switch, split-tunnel UI, WARP/Tor/Psiphon or multi-hop feature was added.

## Validation

- Unit tests have been added but their execution result is pending the branch GitHub Actions run.
- Main's previous CI success is not evidence that this branch compiles.
- Do not claim the APK is tested until the current branch's `:app:testDebugUnitTest` and `:app:assembleDebug` jobs complete successfully.
- Do not claim real-device VPN behavior until physical-device tests pass.

## Phase 2 status

- Code commit tested by CI: `434752617b0fcfa6bb7fdf538e2d8d67ecb1a2dd`.
- CI run: https://github.com/mostafaazad369-web/Mle-vpn-/actions/runs/46124402262 (success).
- Draft PR: https://github.com/mostafaazad369-web/Mle-vpn-/pull/12 (open, not merged).

- [x] Created isolated branch from current audited main.
- [x] Removed blocking runBlocking shutdown from the service lifecycle.
- [x] Added idempotent asynchronous shutdown scheduling.
- [x] Preserved TUN ownership if native shutdown is not confirmed.
- [x] Propagated libbox stop and startup cleanup errors.
- [x] Redacted startup/shutdown diagnostics and UI failure messages.
- [x] Made per-app routing rule application fail closed.
- [x] Added typed ProfileVault load result and UI warning.
- [x] Added unit tests for rule failure, diagnostic redaction and vault load results.
- [x] Branch CI verified successfully on code commit `434752617b0fcfa6bb7fdf538e2d8d67ecb1a2dd` (run `46124402262`): Unit Tests passed, Debug APK assembly passed, artifact uploaded.
- [x] Draft PR #12 opened: https://github.com/mostafaazad369-web/Mle-vpn-/pull/12 (not merged).
- [ ] Physical Android device validation.

## Next step — only after CI and user approval

1. Fix any branch CI failures.
2. Review START/STOP/revoke/destroy concurrency, preferably by extracting a pure lifecycle coordinator with deterministic coroutine tests.
3. Test on Android devices/emulators with actual libbox native runtime.
4. Only then consider moving to DNS implementation or broader VPN feature work.

## Rules

- Re-fetch current GitHub state before making changes.
- Keep `main` untouched; no merge or auto-merge without explicit user approval.
- Do not delete files or add dependencies without reviewing references, provenance and licenses.
- Work only on the user-approved phase.
- At the end of each phase update this file with exact branch/commit, changed files, test/build results and remaining risks.
- Always provide a complete Persian section titled **پرامپت انتقال پروژه به چت بعدی**.

## پرامپت انتقال پروژه به چت بعدی

تو ادامه‌دهنده پروژه موجود MLE VPN هستی؛ پروژه جدید نساز.

مخزن صحیح:
https://github.com/mostafaazad369-web/Mle-vpn-

شاخه اصلی `main` و commit پایه ممیزی:
`65984ee029642a6121c7099218bb6bd1a0b1396c`

شاخه مرحله دوم:
`hardening/phase-2-lifecycle`

گزارش مرجع:
`PROJECT_HANDOVER.md` در همین شاخه را کامل بخوان و وضعیت فعلی GitHub را دوباره بررسی کن.

در این مرحله کاربر اجازه سخت‌سازی Lifecycle و امنیت را داده است. تغییرات انجام‌شده:
- حذف runBlocking از onDestroy و انتقال خاموش‌سازی به coroutine غیرمسدودکننده
- یکپارچه‌سازی مسیرهای stop/revoke/startup-failure/destroy با shutdown idempotent
- نگه‌داشتن TUN و سرویس تا زمانی که خاموش‌شدن libbox تأیید نشده باشد
- انتقال خطاهای closeService/close به caller و حفظ cleanup failures
- redaction پیام خطا و عدم ثبت stacktrace خام حاوی جزئیات احتمالی کانفیگ
- fail-closed کردن اعمال package allow/deny
- نتیجه typed برای load پروفایل‌ها و نمایش خطای Vault در UI
- تست‌های واحد جدید برای fail-closed، redaction و Vault

فایل‌های تغییرکرده و جزئیات را با compare شاخه نسبت به main و همین گزارش بررسی کن.

قوانین:
1. main را تغییر نده.
2. هیچ PR را Merge نکن و Auto-merge فعال نکن.
3. تغییرات فقط روی branch مستقل و در PR Draft ارائه شوند.
4. CI را واقعاً بررسی کن؛ نتیجه pending یا موفقیت main را موفقیت branch تلقی نکن.
5. تست واقعی روی گوشی را از تست واحد و CI جدا گزارش کن.
6. هیچ ادعای DNS/IPv6 leak protection یا Kill Switch بدون تست واقعی مطرح نکن.
7. فایل یا dependency را بدون بررسی حذف/اضافه نکن.
8. قبل از شروع مرحله جدید از کاربر اجازه بگیر.
9. در پایان مرحله، این گزارش را به‌روز و یک پرامپت کامل فارسی ارائه کن.

CI روی commit `434752617b0fcfa6bb7fdf538e2d8d67ecb1a2dd` موفق شده است: تست‌های واحد و ساخت Debug APK هر دو پاس شده‌اند. لینک اجرای CI در گزارش آمده است. ابتدا وضعیت فعلی GitHub و آخرین commit را دوباره بررسی کن؛ سپس گزارش تغییرات و محدودیت‌ها را به کاربر بده و برای تست دستگاه واقعی و مرحله بعد تأیید بگیر. هیچ قابلیت DNS جدیدی را بدون اجازه اضافه نکن.


---

# MLE VPN — Phase 3 Serialized Lifecycle Handover

## Checkpoint
- Repository: https://github.com/mostafaazad369-web/Mle-vpn-
- Main remains at baseline: 65984ee029642a6121c7099218bb6bd1a0b1396c
- Phase 2 branch: hardening/phase-2-lifecycle
- Phase 2 head: ce11437e819d075d04677f55ee7b9d7a8fd8e5b2
- Phase 2 Draft PR #12: https://github.com/mostafaazad369-web/Mle-vpn-/pull/12 (open, not merged)
- Phase 3 branch: hardening/phase-3-serialized-lifecycle
- Phase 3 Draft PR #13: https://github.com/mostafaazad369-web/Mle-vpn-/pull/13 (open, not merged)
- PR #13 currently targets main so the existing CI workflow runs. It includes phase 2 commits plus phase 3 until PR #12 is merged and PR #13 is retargeted/rebased as appropriate.
- No changes to main; no PR merged; no auto-merge.

## Phase 3 scope and changes
Goal: serialize Android VPN lifecycle mutations so start/reload and stop/close are not executed concurrently.

Files changed relative to phase 2:
1. app/src/main/java/com/mle/vpn/engine/VpnLifecycleCoordinator.kt — new FIFO command coordinator backed by a single coroutine consumer and an unlimited Channel. Commands are enqueued synchronously by Android service callbacks.
2. app/src/main/java/com/mle/vpn/engine/MleVpnService.kt — routes START and STOP operations through the coordinator; retains Android startId on both START and explicit STOP; uses stopSelfResult(stopStartId) so an older stop does not stop a service that has received a newer start; preserves the policy of closing TUN only after libbox shutdown succeeds; shutdown failure leaves the service available for retry.
3. app/src/test/java/com/mle/vpn/engine/VpnLifecycleCoordinatorTest.kt — tests FIFO START/STOP/START ordering and verifies a failed START does not prevent the following STOP command.

## Validation
- GitHub Actions workflow: Android CI, workflow file .github/workflows/android-ci.yml.
- CI run for the phase 3 head is tracked from the Actions page linked in PR #13. At the time this handover was written, the run was pending/in progress; do not claim tests or APK assembly passed until the final conclusion is success.
- No local Gradle execution was performed by the authoring session.
- No physical Android device test was performed.
- No DNS/IPv4/IPv6 leak, network handover, Android revoke, process-death, or native TUN ownership claim is made.

## Remaining risks and required review
- Review command-queue behavior when Android destroys the service while a command is in flight.
- Verify rapid START/STOP/START, startup failure followed by a newer START, repeated STOP, revoke and shutdown retry on real Android versions.
- The coordinator serializes commands but cannot guarantee completion after hard process death.
- Confirm stopSelfResult(startId) behavior with actual Android service start IDs and foreground-service constraints on API 24–35.
- Device testing remains a release gate. Keep the APK as a debug/test build only.

## Next step
1. Confirm the latest PR #13 CI run completed successfully. If it failed, inspect the failing job logs and fix only the relevant issue on this branch.
2. Review the phase 3 diff and the interaction with phase 2 before considering either PR for merge.
3. Do not merge PR #12 or #13 without explicit owner approval.
4. After the user is ready, provide one combined device-test build and test START/STOP/START, repeated STOP, startup failure/retry, revoke, Wi-Fi/mobile handover, and force-stop/relaunch.
5. Only after these lifecycle checks should the next implementation phase address DNS runtime integration and leak testing.

## Rules
- Keep main untouched.
- No PR merge or auto-merge without explicit user approval.
- Never report CI success until the latest head SHA has a successful run.
- Separate CI/unit tests from physical-device validation.
- Never claim production readiness, DNS leak prevention, IPv6 leak prevention or kill-switch guarantees without real tests.
- Always update this handover and provide a complete Persian next-chat prompt at the end of each phase.

## پرامپت انتقال پروژه به چت بعدی

تو ادامه‌دهنده پروژه موجود MLE VPN هستی؛ پروژه جدید نساز.

مخزن صحیح:
https://github.com/mostafaazad369-web/Mle-vpn-

هدف: اپ اندرویدی DNS + VPN واقعی، امن و قابل انتشار با Kotlin، Jetpack Compose، Android VpnService و sing-box/libbox. قابلیت‌های DNS، full-tunnel، split tunneling، IPv6، kill switch، WARP/Tor/Psiphon و multi-hop فقط پس از پیاده‌سازی و تست واقعی قابل ادعا هستند.

وضعیت Git:
- main baseline: 65984ee029642a6121c7099218bb6bd1a0b1396c و نباید بدون اجازه تغییر کند.
- Phase 2: branch hardening/phase-2-lifecycle, head ce11437e819d075d04677f55ee7b9d7a8fd8e5b2, Draft PR #12:
  https://github.com/mostafaazad369-web/Mle-vpn-/pull/12
- Phase 3: branch hardening/phase-3-serialized-lifecycle, آخرین head را از GitHub دوباره بخوان، Draft PR #13:
  https://github.com/mostafaazad369-web/Mle-vpn-/pull/13
- PR #13 فعلاً base=main دارد تا CI اجرا شود و شامل phase 2 و phase 3 است. PR #12 و PR #13 هیچ‌کدام merge نشده‌اند. هیچ auto-merge فعال نکن.

Phase 2 تغییرات: خاموش‌سازی غیرمسدودکننده، نگهداری TUN تا تأیید توقف libbox، انتقال خطاهای cleanup، redaction پیام‌های خطا، fail-closed برای package routing rules و نتیجه typed برای خطای ProfileVault. CI موفق قبلی run #46124402262 روی commit کد 434752617b0fcfa6bb7fdf538e2d8d67ecb1a2dd بوده است؛ تست فیزیکی انجام نشده.

Phase 3 تغییرات:
- فایل جدید VpnLifecycleCoordinator.kt: صف FIFO فرمان‌های START/STOP با یک مصرف‌کننده coroutine.
- MleVpnService.kt: اجرای START/STOP از طریق coordinator، نگهداری startId و استفاده از stopSelfResult(startId) تا STOP قدیمی START جدید را متوقف نکند؛ TUN تنها پس از موفقیت shutdown libbox بسته می‌شود.
- فایل تست جدید VpnLifecycleCoordinatorTest.kt: ترتیب START/STOP/START و ادامه کار پس از failure.
- CI جدید را از PR #13 بررسی کن. وضعیت pending/in progress را موفق اعلام نکن. فقط اگر آخرین head SHA دارای conclusion=success بود، Unit Test و assembleDebug را موفق اعلام کن.
- هیچ تست گوشی انجام نشده است. APK Debug را نسخه آزمایشی تلقی کن.

فایل‌های مرجع را بخوان:
- docs/ROADMAP.md
- docs/ENGINE_INTEGRATION_PLAN.md
- PROJECT_HANDOVER.md

محدودیت‌ها:
- تست واقعی start/stop سریع، revoke، process death، foreground service، network handover، TUN native، DNS/IPv4/IPv6 leak انجام نشده است.
- پروژه production-ready نیست.
- اگر CI شکست خورد، فقط همان مشکل را روی branch phase 3 رفع کن و CI جدید اجرا کن.
- اگر CI موفق شد، گزارش دقیق بده و برای بررسی/تست واقعی دستگاه آماده شو.
- قبل از هر مرحله جدید، هدف و محدوده را مشخص کن.
- main را تغییر نده؛ PR را merge نکن؛ فایل/dependency را بدون بررسی حذف/اضافه نکن.
- پایان مرحله باید گزارش کامل و پرامپت انتقال فارسی داشته باشد.


## Phase 3 CI result update (2026-11-28)
- Latest head SHA: 864499922370f8774cccd5442f2e4405bd3cab8a
- Workflow run #46126240570: SUCCESS
- Unit tests: SUCCESS
- Debug APK assembly: SUCCESS
- Artifact upload: SUCCESS
- Artifact name: MLE-VPN-debug
- Artifact ID: 15255564434
- Archive size: 132,973,654 bytes
- SHA-256: 76ec14394507bf951aa01728cc2ee16dc2afe3a485eee6a4e38ee40559673497
- Artifact expires: 2026-12-05T08:33:37Z
- Download page: https://github.com/mostafaazad369-web/Mle-vpn-/actions/runs/46126240570
- Direct ZIP endpoint (requires repository access/authentication): https://api.github.com/repos/mostafaazad369-web/Mle-vpn-/actions/artifacts/15255564434/zip
- CI success applies to this exact branch head. It is not device validation.
- PR #13 remains open, draft, unmerged; main remains unchanged.
