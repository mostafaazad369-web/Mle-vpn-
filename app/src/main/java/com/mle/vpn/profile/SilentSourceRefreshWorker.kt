package com.mle.vpn.profile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** A refresh counts as successful only if this execution validated a feed online. */
internal fun hasNewValidatedSourceFetch(previousSuccessMs: Long, currentSuccessMs: Long): Boolean =
    currentSuccessMs > previousSuccessMs

/**
 * Periodic, bounded refresh of the silent public profile catalog.
 * No source URL or provider identity is surfaced through notifications or UI.
 */
class SilentSourceRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val prefs = applicationContext
            .getSharedPreferences("mle_silent_sources_v1", Context.MODE_PRIVATE)
        val previousSuccess = prefs.getLong("last_successful_fetch_ms", 0L)
        SilentSourceSync.refresh(applicationContext)
        val currentSuccess = prefs.getLong("last_successful_fetch_ms", 0L)
        if (hasNewValidatedSourceFetch(previousSuccess, currentSuccess)) Result.success()
        else if (runAttemptCount < 5) Result.retry()
        else Result.failure()
    } catch (_: Exception) {
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
