package com.mle.vpn.profile

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Curated public Telegram channel names; availability is checked only on user request. */
object TelegramSourceCatalog {
    private val channelPattern = Regex("[A-Za-z0-9_]{5,64}")

    fun load(assets: AssetManager): List<String> =
        assets.open("telegram_sources.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") && channelPattern.matches(it) }
                .distinctBy(String::lowercase)
                .toList()
        }

    fun publicPreviewUrl(channel: String): String {
        require(channelPattern.matches(channel)) { "نام کانال تلگرام معتبر نیست." }
        return "https://t.me/s/$channel"
    }
}

/**
 * Fetches a public Telegram web preview on explicit user action.
 * This does not join channels, use a Telegram account, or bypass private-channel access.
 */
object TelegramChannelFetcher {
    private const val MAX_BYTES = 1_500_000
    private const val MAX_REDIRECTS = 3
    private val shareLink = Regex(
        """(?i)(?:vless|vmess|trojan|ss|hysteria2?|hy2|tuic|anytls|wireguard|socks)://[^\s\"'<>]+"""
    )

    suspend fun fetchShareLinks(channel: String): String = withContext(Dispatchers.IO) {
        var current = URI(TelegramSourceCatalog.publicPreviewUrl(channel))
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            require(current.scheme.equals("https", true) && current.host in setOf("t.me", "telegram.me")) {
                "تغییر مسیر خارج از دامنه امن تلگرام رد شد."
            }
            val connection = (URL(current.toASCIIString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MLE-VPN/1.0)")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirectIndex < MAX_REDIRECTS) { "تعداد تغییر مسیرها بیش از حد است." }
                    current = current.resolve(connection.getHeaderField("Location") ?: error("تغییر مسیر نامعتبر است."))
                    return@repeat
                }
                require(status in 200..299) { "کانال در دسترس نیست (HTTP $status)." }
                val declared = connection.contentLengthLong
                require(declared == -1L || declared <= MAX_BYTES) { "صفحه کانال بیش از حد بزرگ است." }
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(output.length + count <= MAX_BYTES) { "صفحه کانال بیش از حد بزرگ است." }
                        output.append(buffer, 0, count)
                    }
                    output.toString()
                }
                val normalized = html
                    .replace("&amp;", "&")
                    .replace("&#x2F;", "/")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                val links = shareLink.findAll(normalized)
                    .map { it.value.trimEnd('.', ',', ';', ')', ']', '}') }
                    .distinct()
                    .toList()
                require(links.isNotEmpty()) { "در صفحه عمومی این کانال لینک کانفیگ قابل‌خواندنی پیدا نشد." }
                return@withContext links.joinToString("\n")
            } finally {
                connection.disconnect()
            }
        }
        error("دریافت کانال تکمیل نشد.")
    }
}
