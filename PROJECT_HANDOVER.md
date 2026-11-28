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
