package com.mle.vpn.profile

import android.util.Base64
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ShadowsocksCredentials(
    val method: String,
    val password: String,
    val host: String,
    val port: Int,
    val name: String
)

/**
 * Parses SIP002 and legacy ss:// links.
 * Plugin options are rejected until a matching plugin runtime is bundled.
 */
object ShadowsocksLinkParser {
    private val methodPattern = Regex("[A-Za-z0-9_-]+(?:-[A-Za-z0-9_-]+)*")

    fun parse(raw: String): ShadowsocksCredentials {
        require(raw.startsWith("ss://", ignoreCase = true)) { "لینک Shadowsocks معتبر نیست." }
        val withoutScheme = raw.substringAfter("://")
        val withoutFragment = withoutScheme.substringBefore('#')
        val payloadAndQuery = withoutFragment
        val authority = payloadAndQuery.substringBefore('?')
        val query = payloadAndQuery.substringAfter('?', "")
        require(query.isBlank() || query.split('&').none { it.substringBefore('=').equals("plugin", true) }) {
            "این کانفیگ Shadowsocks به افزونه نیاز دارد که هنوز در برنامه نصب نشده است."
        }

        val decodedAuthority = if (authority.contains('@')) {
            val userInfo = authority.substringBeforeLast('@')
            val serverPart = authority.substringAfterLast('@')
            val decodedUserInfo = decodeUserInfo(userInfo)
            "$decodedUserInfo@$serverPart"
        } else {
            decodeBase64(authority)
        }

        val separator = decodedAuthority.lastIndexOf('@')
        require(separator > 0 && separator < decodedAuthority.lastIndex) {
            "اطلاعات رمزگذاری‌شده Shadowsocks قابل خواندن نیست."
        }
        val userInfo = decodedAuthority.substring(0, separator)
        val serverPart = decodedAuthority.substring(separator + 1)
        val methodPassword = decodeUserInfo(userInfo)
        val colon = methodPassword.indexOf(':')
        require(colon > 0 && colon < methodPassword.lastIndex) { "روش رمزنگاری یا رمز Shadowsocks ناقص است." }
        val method = methodPassword.substring(0, colon)
        val password = methodPassword.substring(colon + 1)
        require(methodPattern.matches(method)) { "روش رمزنگاری Shadowsocks معتبر نیست." }
        require(password.isNotBlank()) { "رمز Shadowsocks خالی است." }

        val serverUri = URI("ss://$serverPart")
        val host = serverUri.host ?: error("آدرس سرور Shadowsocks معتبر نیست.")
        val port = serverUri.port
        require(port in 1..65535) { "پورت Shadowsocks معتبر نیست." }
        val fragmentName = raw.substringAfter('#', "").takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }
        return ShadowsocksCredentials(method, password, host, port, fragmentName ?: "Shadowsocks • $host")
    }

    private fun decodeUserInfo(value: String): String {
        val decoded = runCatching { decodeBase64(value) }.getOrNull()
        return decoded ?: URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }

    private fun decodeBase64(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return String(Base64.decode(padded, Base64.DEFAULT), StandardCharsets.UTF_8)
    }
}
