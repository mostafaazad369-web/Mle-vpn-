package com.mle.vpn.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box 1.14-compatible client configuration for review/export.
 * This does not start libbox or establish an Android VPN tunnel.
 */
object SingBoxConfigBuilder {
    fun build(profile: ImportedProfile): JSONObject {
        val proxy = SingBoxOutboundBuilder.build(profile)
        val inbounds = JSONArray().put(
            JSONObject()
                .put("type", "tun")
                .put("tag", "tun-in")
                .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
                .put("mtu", 1400)
                .put("auto_route", true)
                .put("strict_route", true)
                .put("dns_mode", "hijack")
        )
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
        val dns = JSONObject()
            .put("servers", JSONArray().put(dnsServer))
            .put("final", "secure-dns")
            .put("strategy", "prefer_ipv4")
        val route = JSONObject()
            .put("final", "proxy")
            .put("rules", JSONArray().put(
                JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns")
            ))
        return JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("dns", dns)
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("route", route)
    }
}
