package com.mle.vpn.engine.vpngate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Fetches VPN Gate's public relay CSV only on explicit user action.
 * HTTPS-only, bounded response, bounded redirects, and no third-party proxy fallback.
 */
object VpnGateRepository {
    private const val API = "https://www.vpngate.net/api/iphone/"
    private const val MAX_BYTES = 5_000_000
    private const val MAX_REDIRECTS = 2

    suspend fun fetchPublicServers(): List<VpnGateServer> = withContext(Dispatchers.IO) {
        var current = URI(API)
        repeat(MAX_REDIRECTS + 1) { redirect ->
            require(current.scheme.equals("https", true) &&
                current.host?.lowercase() in setOf("www.vpngate.net", "vpngate.net") &&
                current.userInfo == null) {
                "VPN Gate redirect outside the official HTTPS host was rejected."
            }
            val connection = (URL(current.toASCIIString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "text/csv,text/plain")
                setRequestProperty("User-Agent", "MLE-VPN-Android")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirect < MAX_REDIRECTS) { "Too many VPN Gate redirects." }
                    current = current.resolve(connection.getHeaderField("Location")
                        ?: error("VPN Gate redirect location missing."))
                    return@repeat
                }
                require(status in 200..299) { "VPN Gate request failed (HTTP $status)." }
                require(connection.contentLengthLong == -1L ||
                    connection.contentLengthLong <= MAX_BYTES) { "VPN Gate response is too large." }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(out.length + count <= MAX_BYTES) { "VPN Gate response is too large." }
                        out.append(buffer, 0, count)
                    }
                    out.toString()
                }
                val servers = VpnGateCsvParser.parse(body)
                require(servers.isNotEmpty()) { "VPN Gate returned no usable OpenVPN relays." }
                return@withContext servers
            } finally {
                connection.disconnect()
            }
        }
        error("VPN Gate request did not complete.")
    }
}
