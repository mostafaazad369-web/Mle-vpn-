package com.mle.vpn.engine.openvpn

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Validated server-pushed TUN settings for OpenVPN.
 *
 * This object deliberately contains only Android-independent data. The service
 * translates it to VpnService.Builder after the OpenVPN engine owns the VPN lease.
 */
data class OpenVpnTunConfig(
    val mtu: Int,
    val addresses: List<IpPrefix>,
    val routes: List<IpPrefix>,
    val dnsServers: List<String>,
    val searchDomains: List<String>
) {
    data class IpPrefix(val address: String, val prefixLength: Int, val ipv6: Boolean)

    class Builder {
        private var mtu = 1500
        private val addresses = linkedSetOf<IpPrefix>()
        private val routes = linkedSetOf<IpPrefix>()
        private val dns = linkedSetOf<String>()
        private val domains = linkedSetOf<String>()

        fun reset() {
            mtu = 1500
            addresses.clear()
            routes.clear()
            dns.clear()
            domains.clear()
        }

        fun setMtu(value: Int) {
            require(value in 1280..9000) { "OpenVPN MTU must be between 1280 and 9000." }
            mtu = value
        }

        fun addAddress(address: String, prefixLength: Int) {
            require(addresses.size < MAX_ADDRESSES) { "Too many OpenVPN interface addresses." }
            addresses += parsePrefix(address, prefixLength)
        }

        fun addRoute(address: String, prefixLength: Int) {
            require(routes.size < MAX_ROUTES) { "Too many OpenVPN routes." }
            routes += parsePrefix(address, prefixLength)
        }

        fun addDns(address: String) {
            require(dns.size < MAX_DNS) { "Too many OpenVPN DNS servers." }
            val parsed = parseIp(address)
            dns += parsed.hostAddress ?: error("Invalid DNS address.")
        }

        fun addSearchDomain(domain: String) {
            require(domains.size < MAX_DOMAINS) { "Too many OpenVPN search domains." }
            val ascii = IDN.toASCII(domain.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
            require(ascii.length in 1..253 && ascii.split('.').all { it.isNotEmpty() && it.length <= 63 }) {
                "Invalid OpenVPN search domain."
            }
            domains += ascii.lowercase()
        }

        fun build(): OpenVpnTunConfig {
            require(addresses.isNotEmpty()) { "OpenVPN did not provide a TUN interface address." }
            require(routes.isNotEmpty()) { "OpenVPN did not provide any TUN routes." }
            require(addresses.size <= MAX_ADDRESSES && routes.size <= MAX_ROUTES)
            return OpenVpnTunConfig(mtu, addresses.toList(), routes.toList(), dns.toList(), domains.toList())
        }

        private fun parsePrefix(value: String, prefix: Int): IpPrefix {
            val ip = parseIp(value)
            val bits = if (ip is Inet4Address) 32 else 128
            require(prefix in 0..bits) { "Invalid IP prefix length." }
            return IpPrefix(ip.hostAddress, prefix, ip is Inet6Address)
        }

        private fun parseIp(value: String): InetAddress {
            require(value.isNotBlank() && value.length <= 45) { "Invalid IP address." }
            require(value.all {
                it in '0'..'9' || it in ".:" || it in 'a'..'f' || it in 'A'..'F'
            }) { "Only numeric IP literals are accepted." }
            require(value.count { it == ':' } > 0 || value.count { it == '.' } == 3) {
                "Invalid IP literal."
            }
            val parsed = InetAddress.getByName(value)
            require(parsed is Inet4Address || parsed is Inet6Address) { "Invalid IP address." }
            return parsed
        }

        private companion object {
            const val MAX_ADDRESSES = 16
            const val MAX_ROUTES = 128
            const val MAX_DNS = 8
            const val MAX_DOMAINS = 16
        }
    }
}
