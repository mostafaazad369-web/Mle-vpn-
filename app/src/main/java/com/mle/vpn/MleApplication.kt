package com.mle.vpn

import android.app.Application
import android.os.Build
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.Locale

/**
 * Owns one-time native libbox initialization.
 *
 * This prepares the native runtime only. It deliberately does not start a
 * CommandServer or establish a VPN interface.
 */
class MleApplication : Application() {
    private val setupMutex = Mutex()
    @Volatile private var setupComplete = false
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        check(filesDir.exists() || filesDir.mkdirs()) { "Unable to create app files directory." }
        check(cacheDir.exists() || cacheDir.mkdirs()) { "Unable to create app cache directory." }
        scheduleSilentSourceRefresh()
        applicationScope.launch {
            runCatching { ensureLibboxSetup() }
                .onFailure { Log.e(TAG, "libbox initialization failed", it) }
            // Discover and cache compatible fallback profiles without exposing source names or URLs.
            runCatching { com.mle.vpn.profile.SilentSourceSync.refresh(this@MleApplication) }
                .onFailure { Log.w(TAG, "Background profile refresh was unavailable") }
        }
    }

    private fun scheduleSilentSourceRefresh() {
        val request = PeriodicWorkRequestBuilder<com.mle.vpn.profile.SilentSourceRefreshWorker>(
            6, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SILENT_SOURCE_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    suspend fun ensureLibboxSetup() = setupMutex.withLock {
        if (setupComplete) return@withLock

        val workingDirectory = File(filesDir, "libbox")
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            error("Unable to create libbox working directory.")
        }

        Libbox.setLocale(Locale.getDefault().toLanguageTag())
        val options = SetupOptions().apply {
            basePath = filesDir.absolutePath
            workingPath = workingDirectory.absolutePath
            tempPath = cacheDir.absolutePath
            fixAndroidStack = true
            logMaxLines = 1000
            debug = BuildConfig.DEBUG
            crashReportSource = "MLE VPN"
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
            platformMetadata = """{"os":"Android","sdk":${Build.VERSION.SDK_INT},"manufacturer":"${Build.MANUFACTURER}"}"""
        }
        Libbox.setup(options)
        setupComplete = true
    }

    companion object {
        private const val TAG = "MLE-Libbox"
        private const val SILENT_SOURCE_REFRESH_WORK = "mle-silent-source-refresh-v1"
    }
}
