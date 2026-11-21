package com.mle.vpn.profile

import android.content.res.AssetManager
import org.json.JSONObject

data class MlmServiceHint(
    val id: String,
    val title: String,
    val packages: List<String>,
    val domains: List<String>,
    val likelyCensored: Boolean,
    val usesUdp: Boolean,
    val likelyGeoRestricted: Boolean
)

/**
 * Read-only parser for the upstream MAE registry. These records are routing
 * hints only; no automatic probes or steering are performed by this class.
 */
object MlmServiceRegistry {
    private const val MAX_REGISTRY_BYTES = 1_500_000

    fun load(assets: AssetManager): List<MlmServiceHint> {
        val raw = assets.open("mlm/mae/services.json").use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_REGISTRY_BYTES) { "MAE registry is unexpectedly large." }
            bytes.toString(Charsets.UTF_8)
        }
        val root = JSONObject(raw)
        require(root.optInt("schema") == 1) { "Unsupported MAE registry schema." }
        val services = root.getJSONArray("services")
        return (0 until services.length()).map { index ->
            val item = services.getJSONObject(index)
            val hints = item.optJSONObject("hints") ?: JSONObject()
            MlmServiceHint(
                id = item.getString("id"),
                title = item.optString("nameKey").ifBlank { item.getString("id") },
                packages = item.optJSONArray("packages").toStringList(),
                domains = item.optJSONArray("domains").toStringList(),
                likelyCensored = hints.optBoolean("likelyCensored"),
                usesUdp = hints.optBoolean("usesUdp"),
                likelyGeoRestricted = hints.optBoolean("likelyGeoRestricted")
            )
        }.distinctBy { it.id }
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
            .distinct()
    }
}
