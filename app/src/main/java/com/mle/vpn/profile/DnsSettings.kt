package com.mle.vpn.profile

import java.net.Inet6Address
import java.net.InetAddress
import java.net.IDN

enum class DnsProtocol(val label: String) {
    DOH("DNS over HTTPS"),
    DOT("DNS over TLS")
}

enum class DnsProvider(val label: String) {
    CLOUDFLARE("Cloudflare"),
    GOOGLE("Google Public DNS"),
    QUAD9("Quad9"),
    CUSTOM("Custom DNS")
}

/**
 * Strict DNS profile. Remote resolver addresses must be numeric IP literals so
 * establishing the encrypted DNS transport never needs Android/system DNS.
 */
data class DnsSettings(
    val provider: DnsProvider,
    val protocol: DnsProtocol,
    val serverAddress: String,
    val tlsServerName: String,
    val port: Int,
    val dohPath: String = "/dns-query"
) {
    fun validate() {
        require(isIpLiteral(serverAddress)) {
            "DNS server must be a numeric IPv4 or IPv6 address; hostnames are not allowed in strict bootstrap mode."
        }
        require(isValidTlsHostname(tlsServerName)) {
            "A valid TLS server name is required for certificate verification."
        }
        require(port in 1..65535) { "DNS server port must be between 1 and 65535." }
        if (protocol == DnsProtocol.DOH) {
            require(dohPath.startsWith("/") && dohPath.length <= 512 &&
                !dohPath.contains('#') && !dohPath.any(Char::isWhitespace)) {
                "DoH path must be an absolute URL path without fragments or whitespace."
            }
        }
    }

    companion object {
        fun default(): DnsSettings = forProvider(DnsProvider.CLOUDFLARE, DnsProtocol.DOH)

        fun forProvider(provider: DnsProvider, protocol: DnsProtocol): DnsSettings {
            val (address, hostname) = when (provider) {
                DnsProvider.CLOUDFLARE -> "1.1.1.1" to "cloudflare-dns.com"
                DnsProvider.GOOGLE -> "8.8.8.8" to "dns.google"
                DnsProvider.QUAD9 -> "9.9.9.9" to "dns.quad9.net"
                DnsProvider.CUSTOM -> "" to ""
            }
            return DnsSettings(
                provider = provider,
                protocol = protocol,
                serverAddress = address,
                tlsServerName = hostname,
                port = if (protocol == DnsProtocol.DOH) 443 else 853
            )
        }

        private fun isIpLiteral(value: String): Boolean {
            if (value.isBlank() || value.length > 45 || '%' in value) return false
            if (value.contains(':')) {
                if (!value.matches(Regex("[0-9a-fA-F:.]+"))) return false
                return runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
            }
            val parts = value.split('.')
            return parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 &&
                    part.all(Char::isDigit) && (part.toIntOrNull()?.let { it in 0..255 } == true)
            }
        }

        private fun isValidTlsHostname(value: String): Boolean {
            if (value.isBlank() || value.length > 253 || value.endsWith(".")) return false
            val ascii = runCatching { IDN.toASCII(value) }.getOrNull() ?: return false
            if (ascii.length > 253 || ascii.contains(':')) return false
            return ascii.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }
        }
    }
}
