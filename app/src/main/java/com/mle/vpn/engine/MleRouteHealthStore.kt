package com.mle.vpn.engine

import android.content.Context
import org.json.JSONObject

/**
 * Small per-install route history. It stores measurements only (never profile
 * URLs, credentials or generated sing-box JSON). Scores decay through EWMA.
 */
class MleRouteHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mle_route_health_v1", Context.MODE_PRIVATE)

    data class Health(
        val attempts: Int,
        val successes: Int,
        val latencyEwmaMs: Double?,
        val consecutiveFailures: Int,
        val lastSuccessAtMs: Long
    ) {
        val successRate: Double get() = if (attempts == 0) 0.0 else successes.toDouble() / attempts
    }

    fun read(id: String): Health {
        val json = runCatching { JSONObject(prefs.getString(key(id), null).orEmpty()) }.getOrNull()
            ?: return Health(0, 0, null, 0, 0)
        return Health(
            attempts = json.optInt("attempts").coerceAtLeast(0),
            successes = json.optInt("successes").coerceAtLeast(0),
            latencyEwmaMs = if (json.has("latency")) json.optDouble("latency").takeIf { it.isFinite() && it >= 0 } else null,
            consecutiveFailures = json.optInt("failures").coerceAtLeast(0),
            lastSuccessAtMs = json.optLong("lastSuccess").coerceAtLeast(0)
        )
    }

    fun recordSuccess(id: String, latencyMs: Long) {
        val old = read(id)
        val sample = latencyMs.coerceAtLeast(0).toDouble()
        val ewma = old.latencyEwmaMs?.let { it * 0.7 + sample * 0.3 } ?: sample
        write(id, Health(old.attempts + 1, old.successes + 1, ewma, 0, System.currentTimeMillis()))
    }

    fun recordFailure(id: String) {
        val old = read(id)
        write(id, old.copy(attempts = old.attempts + 1, consecutiveFailures = old.consecutiveFailures + 1))
    }

    fun rank(ids: List<String>): List<String> = ids.distinct().map { id ->
        val h = read(id)
        val recencyBonus = if (h.lastSuccessAtMs > 0 &&
            System.currentTimeMillis() - h.lastSuccessAtMs < 24 * 60 * 60 * 1000L) 0.05 else 0.0
        val latencyScore = h.latencyEwmaMs?.let { 1.0 / (1.0 + it / 500.0) } ?: 0.0
        val score = h.successRate * 0.65 + latencyScore * 0.30 + recencyBonus -
            h.consecutiveFailures.coerceAtMost(5) * 0.15
        id to score
    }.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { ids.indexOf(it.first) })
        .map { it.first }

    private fun write(id: String, health: Health) {
        val json = JSONObject()
            .put("attempts", health.attempts)
            .put("successes", health.successes)
            .put("failures", health.consecutiveFailures)
            .put("lastSuccess", health.lastSuccessAtMs)
        health.latencyEwmaMs?.let { json.put("latency", it) }
        prefs.edit().putString(key(id), json.toString()).apply()
    }

    private fun key(id: String) = "route_" + id.hashCode().toUInt().toString(16)
}
