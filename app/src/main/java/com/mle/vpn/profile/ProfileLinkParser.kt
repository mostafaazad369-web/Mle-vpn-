package com.mle.vpn.profile

import java.net.URI

/**
 * Strict first-stage URI recognizer. Protocol-specific credential decoding
 * belongs in dedicated parsers; malformed or unsupported links fail closed.
 */
object ProfileLinkParser {
    fun parse(link: String): ProxyProfile? {
        val raw = link.trim()
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val protocol = when (uri.scheme?.lowercase()) {
            "vless" -> ProxyProtocol.VLESS
            "vmess" -> ProxyProtocol.VMESS
            "trojan" -> ProxyProtocol.TROJAN
            "ss" -> ProxyProtocol.SHADOWSOCKS
            "socks", "socks5" -> ProxyProtocol.SOCKS
            else -> ProxyProtocol.UNKNOWN
        }
        if (protocol == ProxyProtocol.UNKNOWN) return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val label = uri.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port"
        return ProxyProfile(protocol, host, port, label, raw)
    }
}
