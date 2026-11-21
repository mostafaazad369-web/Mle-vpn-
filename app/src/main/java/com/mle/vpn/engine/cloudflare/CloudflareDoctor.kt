package com.mle.vpn.engine.cloudflare

import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight Cloudflare trace diagnostic.
 *
 * This is deliberately a diagnostic only: a successful trace request does not create a VPN
 * tunnel and must never be presented as proof that traffic is protected.
 */
data class CloudflareTraceResult(
    val ip: String?,
    val colo: String?,
    val warp: String?,
    val gateway: String?,
    val httpStatus: Int
)

object CloudflareDoctor {
    private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 7_000
    private const val MAX_RESPONSE_CHARS = 32 * 1024

    /** Blocking network call. Invoke from Dispatchers.IO, never from the main thread. */
    fun check(): CloudflareTraceResult {
        val connection = (URL(TRACE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "MLEVPN-CloudflareDoctor/1.0")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Cloudflare trace returned HTTP $status")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(2048)
                while (out.length <= MAX_RESPONSE_CHARS) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    out.append(buffer, 0, count)
                }
                require(out.length <= MAX_RESPONSE_CHARS) { "Cloudflare trace response is too large" }
                out.toString()
            }
            return parse(body, status)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(body: String, httpStatus: Int = 200): CloudflareTraceResult {
        require(httpStatus in 200..299) { "Cloudflare trace request failed" }
        val fields = body.lineSequence()
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim()
            }
            .toMap()
        require(fields.containsKey("ip") || fields.containsKey("colo") || fields.containsKey("warp")) {
            "Cloudflare trace response did not contain expected fields"
        }
        return CloudflareTraceResult(
            ip = fields["ip"]?.takeIf(String::isNotBlank),
            colo = fields["colo"]?.takeIf(String::isNotBlank),
            warp = fields["warp"]?.takeIf(String::isNotBlank),
            gateway = fields["gateway"]?.takeIf(String::isNotBlank),
            httpStatus = httpStatus
        )
    }
}
