package com.mle.vpn.profile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Public node sources mirrored from the FLUX and Amnezia source catalogs in MLM VPN.
 * The private MLM Quick Connect pool is not mirrored: its deployed Worker URL and signing
 * credentials are redacted from the public source repository.
 * Only protocols currently parsed and converted by MLE are exposed here.
 *
 * Public nodes are untrusted community endpoints. A successful download or TCP probe is not
 * proof of VPN connectivity; MleVpnService performs an HTTPS request through the active TUN.
 */
data class PublicNodeSource(
    val id: String,
    val title: String,
    val description: String,
    val primaryUrl: String,
    val mirrorUrl: String,
    val maxBytes: Int = 512 * 1024,
    val maxNodes: Int = 200
)

object PublicNodeSourceCatalog {
    private fun github(repo: String, branch: String, path: String) = Pair(
        "https://raw.githubusercontent.com/$repo/$branch/$path",
        "https://cdn.jsdelivr.net/gh/$repo@$branch/$path"
    )

    private fun source(
        id: String, title: String, description: String,
        repo: String, path: String, maxBytes: Int = 512 * 1024, maxNodes: Int = 200
    ): PublicNodeSource {
        val (raw, mirror) = github(repo, "main", path)
        return PublicNodeSource(id, title, description, raw, mirror, maxBytes, maxNodes)
    }

    /** FLUX sources from MLM: one Cloudflare-fronted list and direct VLESS/Hy2/Trojan lists. */
    private val fluxSources = listOf(
        source(
            "flux-free-configs", "Free-Configs • Cloudflare",
            "کانفیگ‌های وب‌سوکت/TLS پشت CDN؛ مناسب شبکه‌هایی که Cloudflare در دسترس است",
            "patterniha/Free-Configs", "configs.txt", maxBytes = 256 * 1024
        ),
        source(
            "flux-mg-vless", "MatinGhanbari • VLESS",
            "فهرست مستقیم VLESS/REALITY؛ وابسته به دسترسی سرورهای منتشرشده",
            "MatinGhanbari/v2ray-configs", "subscriptions/filtered/subs/vless.txt"
        ),
        source(
            "flux-mg-hy2", "MatinGhanbari • Hysteria 2",
            "فهرست مستقیم Hysteria 2؛ نیازمند پشتیبانی UDP شبکه",
            "MatinGhanbari/v2ray-configs", "subscriptions/filtered/subs/hysteria2.txt"
        ),
        source(
            "flux-mg-trojan", "MatinGhanbari • Trojan",
            "فهرست مستقیم Trojan",
            "MatinGhanbari/v2ray-configs", "subscriptions/filtered/subs/trojan.txt"
        ),
    )

    /** Public Quick Connect feeds copied from MLM's own QuickConnectRepository. */
    private val quickConnectSources = listOf(
        source("quickconnect-global", "MLM Quick Connect • Global", "منبع عمومی Global که در Quick Connect خود MLM استفاده می‌شود",
            "iampedii/whitedns-sub", "base64.txt", maxBytes = 2 * 1024 * 1024, maxNodes = 500),
        source("quickconnect-verified", "MLM Quick Connect • Verified", "فهرست Verified از تجمیع‌گر مورد استفاده Quick Connect در MLM",
            "0xRadikal/Free-v2ray-Configs", "verified/configs.txt", maxBytes = 2 * 1024 * 1024, maxNodes = 500),
        source("quickconnect-fast", "MLM Quick Connect • Fast", "فهرست Fast از تجمیع‌گر مورد استفاده Quick Connect در MLM",
            "0xRadikal/Free-v2ray-Configs", "fast/configs.txt", maxBytes = 2 * 1024 * 1024, maxNodes = 500),
        source("quickconnect-secure", "MLM Quick Connect • Secure", "فهرست Secure از تجمیع‌گر مورد استفاده Quick Connect در MLM",
            "0xRadikal/Free-v2ray-Configs", "secure/configs.txt", maxBytes = 2 * 1024 * 1024, maxNodes = 500),
        source("quickconnect-all", "MLM Quick Connect • All", "فهرست گسترده All از تجمیع‌گر مورد استفاده Quick Connect در MLM",
            "0xRadikal/Free-v2ray-Configs", "all/configs.txt", maxBytes = 2 * 1024 * 1024, maxNodes = 500)
    )

    /** Hysteria2 subset of MLM Amnezia public collectors; WireGuard files are omitted until MLE ships a tested importer. */
    private val amneziaSources = listOf(
        source("amz-morpheus-hy2", "Morpheus • Hysteria 2", "کانفیگ‌های گردآوری‌شده Hysteria 2",
            "morpheusadam/v2ray-config", "subs/bundles/hysteria2.txt"),
        source("amz-fastnodes-hy2", "FastNodes • Hysteria 2", "فهرست Hysteria 2 با بررسی دوره‌ای منتشرکننده",
            "rtwo2/FastNodes", "sub/protocols/hysteria2.txt"),
        source("amz-fastnodes-hy2-2", "FastNodes • Hysteria 2 (۲)", "بخش دوم فهرست Hysteria 2",
            "rtwo2/FastNodes", "sub/protocols/hysteria2_part2.txt"),
        source("amz-fastnodes-hy2-3", "FastNodes • Hysteria 2 (۳)", "بخش سوم فهرست Hysteria 2",
            "rtwo2/FastNodes", "sub/protocols/hysteria2_part3.txt"),
        source("amz-fastnodes-hy2-4", "FastNodes • Hysteria 2 (۴)", "بخش چهارم فهرست Hysteria 2",
            "rtwo2/FastNodes", "sub/protocols/hysteria2_part4.txt"),
        source("amz-vpnconfig-hy2", "VpnConfigCollector • Hysteria 2", "فهرست عمومی گردآوری‌شده",
            "Argh73/VpnConfigCollector", "Splitted-By-Protocol/Hysteria2.txt"),
        source("amz-proxylist-hy2", "Argh94 • Proxy List", "فهرست عمومی Hysteria 2",
            "Argh94/Proxy-List", "Hysteria2.txt"),
        source("amz-autoconfig-hy2", "Argh94 • Auto Config", "فهرست خودکار Hysteria 2",
            "Argh94/V2RayAutoConfig", "configs/Hysteria2.txt"),
        source("amz-vpnclash-hy2", "VpnClashFaCollector • Hysteria 2", "فهرست گردآوری‌شده و بررسی‌شده",
            "10ium/VpnClashFaCollector", "sub/all/hysteria2.txt"),
        source("amz-submod-hy2", "sub-mod • Hysteria 2", "فهرست تجمیعی عمومی",
            "10Dream/sub-mod", "sub/split/protocols/hy2.txt")
    )

    val all: List<PublicNodeSource> = (fluxSources + quickConnectSources + amneziaSources).distinctBy { it.id }
    fun find(id: String): PublicNodeSource? = all.firstOrNull { it.id == id }
}

data class PublicSourceFetchResult(
    val text: String,
    val fromCache: Boolean,
    val sourceUrl: String,
    /** True when this response was confirmed by a successful HTTP request, including 304. */
    val networkValidated: Boolean = !fromCache
)

object PublicSubscriptionFetcher {
    private const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
    private const val MAX_LINES = 500
    private val allowedHosts = setOf("raw.githubusercontent.com", "cdn.jsdelivr.net")

    private fun bounded(text: String, source: PublicNodeSource): String =
        text.lineSequence().take(source.maxNodes).joinToString("\n")

    suspend fun fetch(context: Context, source: PublicNodeSource): PublicSourceFetchResult =
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.filesDir, "public_sources").apply { mkdirs() }
            val key = MessageDigest.getInstance("SHA-256")
                .digest(source.id.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            val bodyFile = File(cacheDir, "$key.txt")
            val metaFile = File(cacheDir, "$key.json")
            val meta = runCatching { JSONObject(metaFile.readText()) }.getOrDefault(JSONObject())
            var lastError: Exception? = null

            for (candidateUrl in listOf(source.primaryUrl, source.mirrorUrl)) {
                try {
                    val url = URL(candidateUrl)
                    require(url.protocol == "https" && url.host in allowedHosts) {
                        "میزبان منبع عمومی مجاز نیست."
                    }
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12_000
                        readTimeout = 20_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "MLE-VPN/1.0 public-node-fetch")
                        setRequestProperty("Accept", "text/plain, */*")
                        if (meta.optString("url") == candidateUrl) {
                            meta.optString("etag").takeIf(String::isNotBlank)?.let {
                                setRequestProperty("If-None-Match", it)
                            }
                            meta.optString("lastModified").takeIf(String::isNotBlank)?.let {
                                setRequestProperty("If-Modified-Since", it)
                            }
                        }
                    }
                    try {
                        val status = connection.responseCode
                        if (status == HttpURLConnection.HTTP_NOT_MODIFIED && bodyFile.exists()) {
                            return@withContext PublicSourceFetchResult(
                                bounded(bodyFile.readText(), source), true, candidateUrl, networkValidated = true
                            )
                        }
                        require(status in 200..299) { "دریافت منبع ناموفق بود (HTTP $status)." }
                        val length = connection.contentLengthLong
                        require(length == -1L || length <= minOf(source.maxBytes, MAX_TOTAL_BYTES)) {
                            "حجم منبع عمومی بیش از حد مجاز است."
                        }
                        val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            val output = StringBuilder()
                            val buffer = CharArray(8192)
                            while (true) {
                                val count = reader.read(buffer)
                                if (count < 0) break
                                require(output.length + count <= minOf(source.maxBytes, MAX_TOTAL_BYTES)) {
                                    "حجم منبع عمومی بیش از حد مجاز است."
                                }
                                output.append(buffer, 0, count)
                            }
                            output.toString()
                        }
                        require(text.isNotBlank() && !text.trimStart().startsWith("<")) {
                            "منبع به‌جای فهرست کانفیگ، صفحه خالی یا HTML برگرداند."
                        }
                        val bounded = text.lineSequence().take(MAX_LINES + 1).toList()
                        require(bounded.size <= MAX_LINES) { "تعداد خطوط منبع بیش از حد مجاز است." }
                        bodyFile.writeText(text)
                        metaFile.writeText(
                            JSONObject()
                                .put("url", candidateUrl)
                                .put("etag", connection.getHeaderField("ETag").orEmpty())
                                .put("lastModified", connection.getHeaderField("Last-Modified").orEmpty())
                                .put("fetchedAt", System.currentTimeMillis())
                                .toString()
                        )
                        return@withContext PublicSourceFetchResult(bounded(text, source), false, candidateUrl)
                    } finally {
                        connection.disconnect()
                    }
                } catch (error: Exception) {
                    lastError = error
                }
            }

            if (bodyFile.exists()) {
                return@withContext PublicSourceFetchResult(
                    bounded(bodyFile.readText(), source), true, meta.optString("url", source.primaryUrl), networkValidated = false
                )
            }
            throw (lastError ?: IllegalStateException("دریافت منبع عمومی ناموفق بود."))
        }
}
