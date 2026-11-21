package com.mle.vpn.profile

import android.content.res.AssetManager

data class PublicConfigSource(
    val id: String,
    val label: String,
    val primaryUrl: String,
    val mirrorUrl: String,
    val encoding: String
)

/**
 * Public, unauthenticated feed registry. Refresh is add-only: callers merge
 * newly parsed profiles into ProfileVault and never replace the user's vault.
 */
object PublicConfigSourceCatalog {
    fun load(assets: AssetManager): List<PublicConfigSource> =
        assets.open("mlm_public_sources.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val value = line.trim()
                if (value.isEmpty() || value.startsWith("#")) return@mapNotNull null
                val fields = value.split('|')
                if (fields.size < 6 || fields[5] != "public") return@mapNotNull null
                val primary = fields[2]
                val mirror = fields[3]
                if (!primary.startsWith("https://") || !mirror.startsWith("https://")) return@mapNotNull null
                PublicConfigSource(fields[0], fields[1], primary, mirror, fields[4])
            }.distinctBy { it.id }.toList()
        }

    suspend fun fetch(source: PublicConfigSource): String {
        val primary = runCatching { SubscriptionFetcher.fetchIfUrl(source.primaryUrl) }
            .getOrNull()?.takeIf { it.isNotBlank() }
        val payload = primary ?: SubscriptionFetcher.fetchIfUrl(source.mirrorUrl)
            ?: error("منبع عمومی پاسخ قابل‌خواندن نداد.")
        return when (source.encoding) {
            "base64" -> decodeBase64(payload)
            "raw-or-base64" -> if (payload.contains("://")) payload else
                runCatching { decodeBase64(payload) }.getOrDefault(payload)
            else -> payload
        }
    }

    private fun decodeBase64(value: String): String {
        val compact = value.filterNot(Char::isWhitespace)
        val decoded = android.util.Base64.decode(compact, android.util.Base64.DEFAULT)
            .toString(Charsets.UTF_8).trim()
        require(decoded.isNotEmpty() && decoded.contains("://")) { "قالب Base64 منبع معتبر نیست." }
        return decoded
    }
}
