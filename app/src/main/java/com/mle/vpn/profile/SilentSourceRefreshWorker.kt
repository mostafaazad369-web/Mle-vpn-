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
        Result.success()
    } catch (_: Exception) {
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
