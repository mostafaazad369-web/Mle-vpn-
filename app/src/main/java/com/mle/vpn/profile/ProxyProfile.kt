package com.mle.vpn.profile

enum class ProxyProtocol { VLESS, VMESS, TROJAN, SHADOWSOCKS, SOCKS, UNKNOWN }

data class ProxyProfile(
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val name: String,
    val rawUri: String,
)
