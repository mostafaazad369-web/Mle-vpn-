package com.mle.vpn.engine

import com.mle.vpn.profile.ImportedProfile
import com.mle.vpn.profile.ProfileProtocol
import com.mle.vpn.profile.SingBoxOutboundBuilder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts only share-link variants that have a reviewed Xray-core mapping.
 * Unsupported sing-box-only protocols are rejected instead of silently altered.
 */
object XrayConfigBuilder {
    fun build(profile: ImportedProfile): JSONObject {
        require(profile.protocol in setOf(ProfileProtocol.VLESS, ProfileProtocol.VMESS, ProfileProtocol.TROJAN)) {
            "این نوع پروفایل فعلاً در موتور Xray پشتیبانی نمی‌شود."
        }
        val normalized = SingBoxOutboundBuilder.build(profile)
        val outbound = when (profile.protocol) {
            ProfileProtocol.VLESS -> vless(normalized)
            ProfileProtocol.VMESS -> vmess(normalized)
            ProfileProtocol.TROJAN -> trojan(normalized)
            else -> error("Unsupported Xray profile.")
        }.put("tag", "proxy")

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "mle-tun")
                    .put("port", 0)
                    .put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "mle0").put("mtu", 1500))
            ))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole")))
            .put("routing", JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put("rules", JSONArray().put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", JSONArray().put("mle-tun"))
                        .put("outboundTag", "proxy")
                )))
    }

    private fun vless(source: JSONObject): JSONObject {
        val user = JSONObject()
            .put("id", source.getString("uuid"))
            .put("encryption", "none")
        source.optString("flow").takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
        val settings = JSONObject().put("vnext", JSONArray().put(
            JSONObject()
                .put("address", source.getString("server"))
                .put("port", source.getInt("server_port"))
                .put("users", JSONArray().put(user))
        ))
        return JSONObject().put("protocol", "vless").put("settings", settings)
            .put("streamSettings", streamSettings(source))
    }

    private fun vmess(source: JSONObject): JSONObject {
        val user = JSONObject()
            .put("id", source.getString("uuid"))
            .put("alterId", source.optInt("alter_id", 0))
            .put("security", source.optString("security").ifBlank { "auto" })
        val settings = JSONObject().put("vnext", JSONArray().put(
            JSONObject()
                .put("address", source.getString("server"))
                .put("port", source.getInt("server_port"))
                .put("users", JSONArray().put(user))
        ))
        return JSONObject().put("protocol", "vmess").put("settings", settings)
            .put("streamSettings", streamSettings(source))
    }

    private fun trojan(source: JSONObject): JSONObject {
        val server = JSONObject()
            .put("address", source.getString("server"))
            .put("port", source.getInt("server_port"))
            .put("password", source.getString("password"))
        return JSONObject().put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings(source))
    }

    private fun streamSettings(source: JSONObject): JSONObject {
        val stream = JSONObject().put("network", "tcp").put("security", "none")
        source.optJSONObject("transport")?.let { transport ->
            when (transport.optString("type")) {
                "ws" -> {
                    stream.put("network", "ws")
                    val headers = transport.optJSONObject("headers") ?: JSONObject()
                    stream.put("wsSettings", JSONObject()
                        .put("path", transport.optString("path", "/"))
                        .put("headers", headers))
                }
                "grpc" -> {
                    stream.put("network", "grpc")
                    stream.put("grpcSettings", JSONObject()
                        .put("serviceName", transport.optString("service_name")))
                }
                "http" -> {
                    stream.put("network", "http")
                    val hosts = transport.optJSONArray("host") ?: JSONArray()
                    stream.put("httpSettings", JSONObject()
                        .put("path", transport.optString("path", "/"))
                        .put("host", hosts))
                }
                "httpupgrade" -> {
                    stream.put("network", "httpupgrade")
                    stream.put("httpupgradeSettings", JSONObject()
                        .put("path", transport.optString("path", "/"))
                        .put("host", transport.optString("host")))
                }
                else -> error("Transport is not supported by the Xray adapter.")
            }
        }

        source.optJSONObject("tls")?.takeIf { it.optBoolean("enabled") }?.let { tls ->
            val reality = tls.optJSONObject("reality")
            if (reality != null && reality.optBoolean("enabled")) {
                stream.put("security", "reality")
                val realitySettings = JSONObject()
                    .put("serverName", tls.optString("server_name"))
                    .put("publicKey", reality.getString("public_key"))
                    .put("shortId", reality.optString("short_id"))
                tls.optJSONObject("utls")?.takeIf { it.optBoolean("enabled") }
                    ?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                    ?.let { realitySettings.put("fingerprint", it) }
                stream.put("realitySettings", realitySettings)
            } else {
                stream.put("security", "tls")
                val tlsSettings = JSONObject()
                tls.optString("server_name").takeIf { it.isNotBlank() }?.let { tlsSettings.put("serverName", it) }
                tls.optJSONArray("alpn")?.let { tlsSettings.put("alpn", it) }
                tls.optJSONObject("utls")?.takeIf { it.optBoolean("enabled") }
                    ?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                    ?.let { tlsSettings.put("fingerprint", it) }
                stream.put("tlsSettings", tlsSettings)
            }
        }
        return stream
    }
}
