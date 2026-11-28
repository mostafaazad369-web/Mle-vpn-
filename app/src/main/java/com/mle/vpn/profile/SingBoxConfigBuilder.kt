package com.mle.vpn.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box 1.14-compatible client configuration.
 * The strict bootstrap resolver is encrypted and uses an IP literal, so it
 * never depends on Android/system DNS to resolve its own endpoint.
 */
object SingBoxConfigBuilder {
    fun build(
        profile: ImportedProfile,
        dnsSettings: DnsSettings = DnsSettings.default()
    ): JSONObject {
        dnsSettings.validate()

        val proxy = SingBoxOutboundBuilder.build(profile)
            .put("domain_resolver", "bootstrap")
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

        // Bootstrap is used only for resolving the proxy endpoint. It uses
        // encrypted DNS to a numeric IP over the physical underlay (direct).
        // Application DNS uses the same provider over the selected proxy.
        // There is intentionally no "local" resolver and no plaintext fallback.
        val bootstrapDns = buildDnsServer(dnsSettings, "bootstrap", "direct")
        val secureDns = buildDnsServer(dnsSettings, "secure-dns", "proxy")
        val dns = JSONObject()
            .put("servers", JSONArray().put(bootstrapDns).put(secureDns))
            .put("final", "secure-dns")
            .put("strategy", "prefer_ipv4")
            .put("timeout", "8s")

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

    private fun buildDnsServer(settings: DnsSettings, tag: String, detour: String): JSONObject {
        val type = if (settings.protocol == DnsProtocol.DOH) "https" else "tls"
        val server = JSONObject()
            .put("type", type)
            .put("tag", tag)
            .put("server", settings.serverAddress)
            .put("server_port", settings.port)
            .put("tls", JSONObject()
                .put("enabled", true)
                .put("server_name", settings.tlsServerName))
            .put("detour", detour)

        if (settings.protocol == DnsProtocol.DOH) {
            server.put("path", settings.dohPath)
            // The transport connects to a numeric IP to avoid bootstrap DNS,
            // while HTTP Host and TLS SNI retain the provider's DNS hostname.
            server.put("headers", JSONObject().put("Host", settings.tlsServerName))
        }
        return server
    }
}
