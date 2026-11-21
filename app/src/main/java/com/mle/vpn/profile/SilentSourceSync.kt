package com.mle.vpn.profile

import android.content.Context
import com.mle.vpn.engine.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Quietly hydrates the encrypted profile vault from bundled public source definitions.
 * Source names and fetch diagnostics are intentionally not exposed to the UI.
 * End-to-end health is decided later by MleVpnService through the active TUN.
 */
object SilentSourceSync {
    private const val MAX_IMPORTED_PROFILES = 120
    private const val MAX_PROFILES_PER_SOURCE = 5
    private const val MAX_SOURCE_ATTEMPTS_PER_REFRESH = 6
    private const val REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    private const val RETRY_AFTER_FAILURE_MS = 5L * 60L * 1000L

    suspend fun refresh(context: Context): Int = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val vault = ProfileVault(appContext)
        val knownUris = vault.loadAll().mapTo(mutableSetOf()) { it.sourceUri }
        val prefs = appContext.getSharedPreferences("mle_silent_sources_v1", Context.MODE_PRIVATE)
        val lastRefresh = prefs.getLong("last_refresh_ms", 0L)
        if (System.currentTimeMillis() - lastRefresh < REFRESH_INTERVAL_MS) return@withContext 0
        val sourceManagedIds = prefs.getStringSet("profile_ids", emptySet()).orEmpty().toMutableSet()
        val sources = PublicNodeSourceCatalog.all
        val startIndex = prefs.getInt("next_source_index", 0).mod(sources.size.coerceAtLeast(1))
        val rotatedSources = sources.drop(startIndex) + sources.take(startIndex)
        var attemptedSources = 0
        var successfulFetches = 0
        var imported = 0

        for (source in rotatedSources) {
            if (imported >= MAX_IMPORTED_PROFILES || attemptedSources >= MAX_SOURCE_ATTEMPTS_PER_REFRESH) break
            attemptedSources++
            val candidates = runCatching {
                val fetched = PublicSubscriptionFetcher.fetch(appContext, source)
                // Cached last-good data remains usable for imports, but it must not
                // masquerade as a successful online refresh or reset retry backoff.
                if (fetched.networkValidated) successfulFetches++
                ProfileImporter.parseBatch(fetched.text).profiles.filter { profile ->
                    profile.sourceUri !in knownUris && (
                        runCatching { SingBoxOutboundBuilder.build(profile) }.isSuccess ||
                            runCatching { XrayConfigBuilder.build(profile) }.isSuccess
                    )
                }
            }.getOrDefault(emptyList())

            if (candidates.isNotEmpty()) {
                candidates.take(minOf(MAX_PROFILES_PER_SOURCE, MAX_IMPORTED_PROFILES - imported)).forEach { profile ->
                    vault.save(profile)
                    knownUris += profile.sourceUri
                    sourceManagedIds += profile.id
                    imported++
                }
            }
        }

        val completedAt = System.currentTimeMillis()
        prefs.edit()
            .putStringSet("profile_ids", sourceManagedIds)
            .putInt("next_source_index", (startIndex + attemptedSources).mod(sources.size.coerceAtLeast(1)))
            .also { editor -> if (successfulFetches > 0) editor.putLong("last_successful_fetch_ms", completedAt) }
            // Do not suppress retries for a full hour when every source is unreachable.
            .putLong(
                "last_refresh_ms",
                if (successfulFetches > 0) completedAt
                else completedAt - REFRESH_INTERVAL_MS + RETRY_AFTER_FAILURE_MS
            )
            .apply()
        imported
    }
}
