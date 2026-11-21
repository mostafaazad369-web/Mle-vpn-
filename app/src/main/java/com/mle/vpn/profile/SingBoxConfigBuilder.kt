package com.mle.vpn.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box 1.14-compatible client configuration for review/export.
 * This does not start libbox or establish an Android VPN tunnel.
 */
object SingBoxConfigBuilder {
    fun build(profile: ImportedProfile, allowedPackages: List<String> = emptyList()): JSONObject {
        val safePackages = if (allowedPackages.isEmpty()) emptyList() else (allowedPackages + "com.mle.vpn").distinct().filter { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }.take(128)
        if (profile.protocol == ProfileProtocol.WIREGUARD || profile.protocol == ProfileProtocol.WARP) return buildWireGuard(profile, safePackages)
        if (profile.protocol == ProfileProtocol.OPENVPN) return buildOpenVpn(profile, safePackages)
        val proxy = SingBoxOutboundBuilder.build(profile)
            .put("domain_resolver", "bootstrap")
        val tunInbound = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            .put("mtu", 1400)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("dns_mode", "hijack")
        if (safePackages.isNotEmpty()) tunInbound.put("include_package", JSONArray(safePackages))
        val inbounds = JSONArray().put(tunInbound)
        val outbounds = JSONArray()
            .put(proxy)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "block").put("tag", "block"))
        val dnsServer = JSONObject()
            .put("type", "https")
            .put("tag", "secure-dns")
            .put("server", "1.1.1.1")
            .put("server_port", 443)
            .put("path", "/dns-query")
            .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com"))
            .put("detour", "proxy")
        // Resolve the proxy endpoint through Android's underlying-network
        // resolver first; otherwise DoH itself would detour through a proxy
        // whose hostname may still need DNS (bootstrap recursion).
        val bootstrapDns = JSONObject()
            .put("type", "local")
            .put("tag", "bootstrap")
        val dns = JSONObject()
            .put("servers", JSONArray().put(bootstrapDns).put(dnsServer))
            .put("final", "secure-dns")
            .put("strategy", "prefer_ipv4")
        // Android's VpnService owns the outer TUN. Tell sing-box to use
        // the Android VPN as the upstream path and bind proxy sockets to the
        // detected underlying network, avoiding a route loop / black-holed TUN.
        val route = JSONObject()
            .put("final", "proxy")
            .put("auto_detect_interface", true)
            .put("override_android_vpn", true)
            .put("default_domain_resolver", "bootstrap")
            .put("rules", JSONArray().put(
                JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns")
            ))
        return JSONObject()
            .put("log", JSONObject().put("level", "info"))
            .put("dns", dns)
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("route", route)
    }


    private fun buildOpenVpn(profile: ImportedProfile, allowedPackages: List<String>): JSONObject {
        val openVpn = OpenVpnConfigParser.decodeStored(profile.sourceUri)
        val endpoint = openVpn.toSingBoxEndpoint("ovpn-ep")
            .put("domain_resolver", "bootstrap")
        val dnsServers = JSONArray()
            .put(JSONObject().put("type", "local").put("tag", "bootstrap"))
            .put(JSONObject()
                .put("type", "https")
                .put("tag", "secure-dns")
                .put("server", "1.1.1.1")
                .put("server_port", 443)
                .put("path", "/dns-query")
                .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com"))
                .put("detour", "ovpn-ep")
                .put("domain_resolver", "bootstrap"))
        val dns = JSONObject()
            .put("servers", dnsServers)
            .put("final", "secure-dns")
            .put("strategy", "prefer_ipv4")
        val route = JSONObject()
            .put("final", "ovpn-ep")
            .put("auto_detect_interface", true)
            .put("override_android_vpn", true)
            .put("default_domain_resolver", "bootstrap")
            .put("rules", JSONArray().put(
                JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns")
            ))
        val tunInbound = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            .put("mtu", openVpn.mtu)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("dns_mode", "hijack")
        if (allowedPackages.isNotEmpty()) tunInbound.put("include_package", JSONArray(allowedPackages))
        return JSONObject()
            .put("log", JSONObject().put("level", "info"))
            .put("dns", dns)
            .put("inbounds", JSONArray().put(tunInbound))
            .put("outbounds", JSONArray()
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block")))
            .put("endpoints", JSONArray().put(endpoint))
            .put("route", route)
    }

    private fun buildWireGuard(profile: ImportedProfile, allowedPackages: List<String>): JSONObject {
        val wireGuard = WireGuardConfigParser.decodeStored(profile.sourceUri)
        val endpoint = wireGuard.toSingBoxEndpoint("wg-ep")
        val dnsServers = JSONArray().put(JSONObject().put("type", "local").put("tag", "bootstrap"))
        val finalDnsTag = if (wireGuard.dnsServers.isNotEmpty()) {
            wireGuard.dnsServers.forEachIndexed { index, server ->
                dnsServers.put(
                    JSONObject()
                        .put("type", "udp")
                        .put("tag", "wg-dns-$index")
                        .put("server", server)
                        .put("detour", "wg-ep")
                        .put("domain_resolver", "bootstrap")
                )
            }
            "wg-dns-0"
        } else {
            dnsServers.put(
                JSONObject()
                    .put("type", "https")
                    .put("tag", "secure-dns")
                    .put("server", "1.1.1.1")
                    .put("server_port", 443)
                    .put("path", "/dns-query")
                    .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com"))
                    .put("detour", "wg-ep")
            )
            "secure-dns"
        }
        val dns = JSONObject()
            .put("servers", dnsServers)
            .put("final", finalDnsTag)
            .put("strategy", "prefer_ipv4")
        val route = JSONObject()
            .put("final", "wg-ep")
            .put("auto_detect_interface", true)
            .put("override_android_vpn", true)
            .put("default_domain_resolver", "bootstrap")
            .put("rules", JSONArray().put(
                JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns")
            ))
        val tunInbound = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            .put("mtu", WireGuardConfigParser.decodeStored(profile.sourceUri).mtu)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("dns_mode", "hijack")
        if (allowedPackages.isNotEmpty()) tunInbound.put("include_package", JSONArray(allowedPackages))
        return JSONObject()
            .put("log", JSONObject().put("level", "info"))
            .put("dns", dns)
            .put("inbounds", JSONArray().put(tunInbound))
            .put("outbounds", JSONArray()
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block")))
            .put("endpoints", JSONArray().put(endpoint))
            .put("route", route)
    }
}
