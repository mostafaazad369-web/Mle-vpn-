package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Converts a limited set of share links into sing-box outbound JSON.
 * Unsupported variants fail closed instead of silently dropping security parameters.
 */
object SingBoxOutboundBuilder {
    fun build(profile: ImportedProfile): JSONObject = when (profile.protocol) {
        ProfileProtocol.VLESS -> buildVless(URI(profile.sourceUri))
        ProfileProtocol.TROJAN -> buildTrojan(URI(profile.sourceUri))
        ProfileProtocol.VMESS -> buildVmess(profile.sourceUri)
        ProfileProtocol.SHADOWSOCKS -> buildShadowsocks(profile.sourceUri)
        ProfileProtocol.HYSTERIA2 -> buildHysteria2(URI(profile.sourceUri))
        ProfileProtocol.TUIC -> buildTuic(URI(profile.sourceUri))
        ProfileProtocol.WIREGUARD -> throw IllegalArgumentException("WireGuard در sing-box 1.14 باید به‌صورت endpoint ساخته شود.")
        else -> throw IllegalArgumentException("تبدیل خودکار این پروتکل هنوز پیاده‌سازی نشده است: ${profile.protocol}")
    }


    fun buildWireGuardEndpoint(profile: ImportedProfile): JSONObject {
        require(profile.protocol == ProfileProtocol.WIREGUARD)
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var section: String? = null
        profile.sourceUri.lineSequence().forEach { original ->
            val line = original.substringBefore('#').substringBefore(';').trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                sections.getOrPut(section!!) { linkedMapOf() }
            } else if (section != null && '=' in line) {
                sections[section!!]?.set(line.substringBefore('=').trim().lowercase(), line.substringAfter('=').trim())
            }
        }
        val iface = sections["interface"] ?: error("بخش [Interface] وجود ندارد.")
        val peer = sections["peer"] ?: error("بخش [Peer] وجود ندارد.")
        val endpoint = java.net.URI("udp://${peer["endpoint"].orEmpty()}")
        val host = endpoint.host ?: error("WireGuard endpoint host نامعتبر است.")
        val port = endpoint.port.takeIf { it in 1..65535 } ?: error("WireGuard endpoint port نامعتبر است.")
        val addresses = iface["address"].orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
        val allowed = peer["allowedips"].orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
        val peerObject = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("public_key", peer.getValue("publickey"))
            .put("allowed_ips", JSONArray(allowed))
        peer["presharedkey"]?.takeIf(String::isNotBlank)?.let { peerObject.put("pre_shared_key", it) }
        peer["persistentkeepalive"]?.toIntOrNull()?.takeIf { it > 0 }?.let { peerObject.put("persistent_keepalive_interval", it) }
        peer["reserved"]?.takeIf(String::isNotBlank)?.let { raw ->
            peerObject.put("reserved", JSONArray(raw.split(',').map { it.trim().toInt() }))
        }
        return JSONObject()
            .put("type", "wireguard")
            .put("tag", "proxy")
            .put("mtu", iface["mtu"]?.toIntOrNull()?.coerceIn(1280, 9000) ?: 1408)
            .put("address", JSONArray(addresses))
            .put("private_key", iface.getValue("privatekey"))
            .put("peers", JSONArray().put(peerObject))
    }

    private fun buildVless(uri: URI): JSONObject {
        val uuid = uri.rawUserInfo?.let(::decode)?.takeIf { it.isNotBlank() }
            ?: error("شناسه UUID در کانفیگ VLESS وجود ندارد.")
        val q = query(uri.rawQuery)
        val security = q["security"].orEmpty().lowercase()
        val type = q["type"].orEmpty().lowercase().ifBlank { "tcp" }
        val out = JSONObject().put("type", "vless").put("tag", "proxy")
            .put("server", uri.host).put("server_port", requirePort(uri.port)).put("uuid", uuid)
        q["flow"]?.takeIf { it.isNotBlank() }?.let { out.put("flow", it) }

        if (security == "tls" || security == "reality") {
            val tls = JSONObject().put("enabled", true)
            q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
            q["alpn"]?.takeIf { it.isNotBlank() }?.let {
                tls.put("alpn", JSONArray(it.split(',').map(String::trim).filter(String::isNotEmpty)))
            }
            q["fp"]?.takeIf { it.isNotBlank() }?.let {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
            }
            if (security == "reality") {
                val publicKey = q["pbk"] ?: error("کلید عمومی Reality (pbk) وجود ندارد.")
                val reality = JSONObject().put("enabled", true).put("public_key", publicKey)
                q["sid"]?.takeIf { it.isNotBlank() }?.let { reality.put("short_id", it) }
                tls.put("reality", reality)
            }
            out.put("tls", tls)
        } else if (security.isNotBlank() && security != "none") {
            error("نوع امنیت VLESS پشتیبانی نمی‌شود: $security")
        }

        when (type) {
            "tcp" -> Unit
            "ws" -> {
                val transport = JSONObject().put("type", "ws")
                q["path"]?.let { transport.put("path", it) }
                q["host"]?.takeIf { it.isNotBlank() }?.let {
                    transport.put("headers", JSONObject().put("Host", it))
                }
                out.put("transport", transport)
            }
            "grpc" -> out.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", q["serviceName"] ?: q["servicename"] ?: ""))
            else -> error("انتقال VLESS از نوع $type هنوز پشتیبانی نمی‌شود.")
        }
        return out
    }

    private fun buildTrojan(uri: URI): JSONObject {
        val password = uri.rawUserInfo?.let(::decode)?.takeIf { it.isNotBlank() }
            ?: error("رمز Trojan وجود ندارد.")
        val q = query(uri.rawQuery)
        val out = JSONObject().put("type", "trojan").put("tag", "proxy")
            .put("server", uri.host).put("server_port", requirePort(uri.port))
            .put("password", password)
        val tls = JSONObject().put("enabled", true)
        q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
        q["alpn"]?.takeIf { it.isNotBlank() }?.let {
            tls.put("alpn", JSONArray(it.split(',').map(String::trim).filter(String::isNotEmpty)))
        }
        out.put("tls", tls)
        when (q["type"]?.lowercase()) {
            null, "", "tcp" -> Unit
            "ws" -> {
                val transport = JSONObject().put("type", "ws")
                q["path"]?.let { transport.put("path", it) }
                q["host"]?.takeIf { it.isNotBlank() }?.let {
                    transport.put("headers", JSONObject().put("Host", it))
                }
                out.put("transport", transport)
            }
            "grpc" -> out.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", q["serviceName"] ?: ""))
            else -> error("انتقال Trojan هنوز پشتیبانی نمی‌شود.")
        }
        return out
    }

    private fun buildShadowsocks(raw: String): JSONObject {
        val parsed = ShadowsocksLinkParser.parse(raw)
        return JSONObject()
            .put("type", "shadowsocks")
            .put("tag", "proxy")
            .put("server", parsed.host)
            .put("server_port", requirePort(parsed.port))
            .put("method", parsed.method)
            .put("password", parsed.password)
    }

    private fun buildHysteria2(uri: URI): JSONObject {
        val password = uri.rawUserInfo?.let(::decode)?.takeIf { it.isNotBlank() }
            ?: error("رمز عبور Hysteria2 وجود ندارد.")
        val q = query(uri.rawQuery)
        val out = JSONObject().put("type", "hysteria2").put("tag", "proxy")
            .put("server", uri.host ?: error("آدرس سرور Hysteria2 معتبر نیست."))
            .put("server_port", requirePort(uri.port))
            .put("password", password)
        val tls = JSONObject().put("enabled", true)
        q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
        q["alpn"]?.takeIf { it.isNotBlank() }?.let {
            tls.put("alpn", JSONArray(it.split(',').map(String::trim).filter(String::isNotEmpty)))
        }
        if (q["insecure"] == "1" || q["allowInsecure"] == "1" || q["allow_insecure"] == "1") {
            tls.put("insecure", true)
        }
        out.put("tls", tls)
        val obfsType = q["obfs"]?.takeIf { it.isNotBlank() }
        val obfsPassword = q["obfs-password"] ?: q["obfs_password"]
        if (obfsType != null) {
            require(obfsType == "salamander") { "نوع obfuscation در Hysteria2 پشتیبانی نمی‌شود: $obfsType" }
            require(!obfsPassword.isNullOrBlank()) { "رمز obfuscation برای Hysteria2 وجود ندارد." }
            out.put("obfs", JSONObject().put("type", obfsType).put("password", obfsPassword))
        } else {
            require(obfsPassword.isNullOrBlank()) { "رمز obfuscation بدون تعیین نوع آن معتبر نیست." }
        }
        q["upmbps"]?.toIntOrNull()?.let { require(it > 0); out.put("up_mbps", it) }
        q["downmbps"]?.toIntOrNull()?.let { require(it > 0); out.put("down_mbps", it) }
        return out
    }

    private fun buildTuic(uri: URI): JSONObject {
        val credentials = uri.rawUserInfo?.let(::decode)
            ?: error("اطلاعات ورود TUIC وجود ندارد.")
        val separator = credentials.indexOf(':')
        require(separator > 0 && separator < credentials.lastIndex) {
            "لینک TUIC باید شامل UUID و رمز عبور باشد."
        }
        val uuid = credentials.substring(0, separator)
        require(runCatching { java.util.UUID.fromString(uuid) }.isSuccess) {
            "UUID کانفیگ TUIC معتبر نیست."
        }
        val password = credentials.substring(separator + 1)
        val q = query(uri.rawQuery)
        val congestion = q["congestion_control"] ?: q["congestion-control"] ?: "cubic"
        require(congestion in setOf("cubic", "new_reno", "bbr")) {
            "الگوریتم کنترل ازدحام TUIC پشتیبانی نمی‌شود."
        }
        val out = JSONObject().put("type", "tuic").put("tag", "proxy")
            .put("server", uri.host ?: error("آدرس سرور TUIC معتبر نیست."))
            .put("server_port", requirePort(uri.port))
            .put("uuid", uuid).put("password", password)
            .put("congestion_control", congestion)
            .put("udp_relay_mode", q["udp_relay_mode"] ?: q["udp-relay-mode"] ?: "native")
            .put("zero_rtt_handshake", false)
        val relayMode = out.getString("udp_relay_mode")
        require(relayMode in setOf("native", "quic")) { "حالت UDP در TUIC معتبر نیست." }
        val tls = JSONObject().put("enabled", true)
        q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
        if (q["insecure"] == "1" || q["allowInsecure"] == "1" || q["allow_insecure"] == "1") {
            tls.put("insecure", true)
        }
        q["alpn"]?.takeIf { it.isNotBlank() }?.let {
            tls.put("alpn", JSONArray(it.split(',').map(String::trim).filter(String::isNotEmpty)))
        }
        out.put("tls", tls)
        return out
    }

    private fun buildVmess(raw: String): JSONObject {
        val encoded = raw.substringAfter("://").substringBefore('#').trim()
        val text = String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
        val vmess = JSONObject(text)
        val host = vmess.optString("add").takeIf { it.isNotBlank() } ?: error("VMess host is missing.")
        val port = requirePort(vmess.optInt("port", -1))
        val uuid = vmess.optString("id").takeIf { it.isNotBlank() } ?: error("VMess UUID is missing.")
        val out = JSONObject().put("type", "vmess").put("tag", "proxy")
            .put("server", host).put("server_port", port).put("uuid", uuid)
            .put("security", vmess.optString("scy").ifBlank { "auto" })
            .put("alter_id", vmess.optInt("aid", 0))
        if (vmess.optString("tls").equals("tls", true)) {
            val tls = JSONObject().put("enabled", true)
            val sni = vmess.optString("sni").ifBlank { vmess.optString("host") }
            if (sni.isNotBlank()) tls.put("server_name", sni)
            out.put("tls", tls)
        }
        when (vmess.optString("net").lowercase()) {
            "", "tcp" -> Unit
            "ws" -> {
                val transport = JSONObject().put("type", "ws")
                val path = vmess.optString("path")
                if (path.isNotBlank()) transport.put("path", path)
                val hostHeader = vmess.optString("host")
                if (hostHeader.isNotBlank()) transport.put("headers", JSONObject().put("Host", hostHeader))
                out.put("transport", transport)
            }
            "grpc" -> out.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", vmess.optString("path")))
            else -> error("VMess transport is not supported: ${vmess.optString("net")}")
        }
        return out
    }

    private fun query(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { it.isNotBlank() }.associate { item ->
            decode(item.substringBefore('=')) to decode(item.substringAfter('=', ""))
        }

    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun requirePort(port: Int): Int {
        require(port in 1..65535) { "پورت سرور معتبر نیست." }
        return port
    }
}
