package com.mle.vpn.profile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic, bounded refresh of the silent public profile catalog.
 * No source URL or provider identity is surfaced through notifications or UI.
 */
class SilentSourceRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        SilentSourceSync.refresh(applicationContext)
        val lastSuccess = applicationContext
            .getSharedPreferences("mle_silent_sources_v1", Context.MODE_PRIVATE)
            .getLong("last_successful_fetch_ms", 0L)
        val successIsRecent = lastSuccess > 0L &&
            System.currentTimeMillis() - lastSuccess < 24L * 60L * 60L * 1000L
        if (successIsRecent) Result.success()
        else if (runAttemptCount < 5) Result.retry()
        else Result.failure()
    } catch (_: Exception) {
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
