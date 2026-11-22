package com.mle.vpn.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Bounded HTTPS-only subscription downloader.
 * It never logs the URL or response because subscriptions may contain secrets.
 */
object SubscriptionFetcher {
    private const val MAX_BYTES = 1_000_000
    private const val MAX_REDIRECTS = 3
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun fetchIfUrl(input: String): String? = withContext(Dispatchers.IO) {
        val raw = input.trim()
        if (!raw.startsWith("https://", ignoreCase = true)) return@withContext null

        var current = URI(raw)
        require(current.scheme.equals("https", true)) { "فقط اشتراک HTTPS پذیرفته می‌شود." }
        require(!current.host.isNullOrBlank() && current.userInfo == null) {
            "آدرس اشتراک معتبر نیست."
        }
        require(current.port == -1 || current.port in 1..65535) { "پورت آدرس اشتراک معتبر نیست." }

        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            require(current.scheme.equals("https", true) && current.userInfo == null) {
                "تغییر مسیر ناامن رد شد؛ فقط HTTPS مجاز است."
            }
            val connection = (URL(current.toASCIIString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/plain, application/octet-stream, */*")
                setRequestProperty("User-Agent", "MLE-VPN-Android")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirectIndex < MAX_REDIRECTS) { "تعداد تغییر مسیرهای اشتراک بیش از حد است." }
                    val location = connection.getHeaderField("Location")
                        ?: error("آدرس تغییر مسیر اشتراک وجود ندارد.")
                    current = current.resolve(location)
                    require(current.scheme.equals("https", true) && current.userInfo == null) {
                        "تغییر مسیر به آدرس غیر HTTPS مجاز نیست."
                    }
                    return@repeat
                }
                require(status in 200..299) { "دریافت اشتراک ناموفق بود (HTTP $status)." }
                val declaredLength = connection.contentLengthLong
                require(declaredLength <= MAX_BYTES || declaredLength == -1L) {
                    "حجم اشتراک از حد مجاز بیشتر است."
                }
                val bytes = connection.inputStream.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "حجم اشتراک از حد مجاز بیشتر است." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val text = bytes.toString(Charsets.UTF_8).trim()
                require(text.isNotEmpty()) { "اشتراک دریافتی خالی است." }
                return@withContext text
            } finally {
                connection.disconnect()
            }
        }
        error("دریافت اشتراک تکمیل نشد.")
    }
}
