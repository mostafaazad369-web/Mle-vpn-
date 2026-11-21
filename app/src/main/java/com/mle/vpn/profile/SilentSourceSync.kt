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
    private const val MIN_HEALTHY_SOURCES = 3

    suspend fun refresh(context: Context): Int = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val vault = ProfileVault(appContext)
        val knownUris = vault.loadAll().mapTo(mutableSetOf()) { it.sourceUri }
        val sourceManagedIds = appContext
            .getSharedPreferences("mle_silent_sources_v1", Context.MODE_PRIVATE)
            .getStringSet("profile_ids", emptySet()).orEmpty().toMutableSet()
        var imported = 0
        var healthySources = 0

        for (source in PublicNodeSourceCatalog.all) {
            if (imported >= MAX_IMPORTED_PROFILES || healthySources >= MIN_HEALTHY_SOURCES) break
            val candidates = runCatching {
                val fetched = PublicSubscriptionFetcher.fetch(appContext, source)
                ProfileImporter.parseBatch(fetched.text).profiles.filter { profile ->
                    profile.sourceUri !in knownUris && (
                        runCatching { SingBoxOutboundBuilder.build(profile) }.isSuccess ||
                            runCatching { XrayConfigBuilder.build(profile) }.isSuccess
                    )
                }
            }.getOrDefault(emptyList())

            if (candidates.isNotEmpty()) {
                healthySources++
                candidates.take(MAX_IMPORTED_PROFILES - imported).forEach { profile ->
                    vault.save(profile)
                    knownUris += profile.sourceUri
                    sourceManagedIds += profile.id
                    imported++
                }
            }
        }

        appContext.getSharedPreferences("mle_silent_sources_v1", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("profile_ids", sourceManagedIds)
            .putLong("last_refresh_ms", System.currentTimeMillis())
            .apply()
        imported
    }
}
