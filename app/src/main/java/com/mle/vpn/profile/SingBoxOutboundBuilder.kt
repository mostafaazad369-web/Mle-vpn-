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
        else -> throw IllegalArgumentException("تبدیل خودکار این پروتکل هنوز پیاده‌سازی نشده است: ${profile.protocol}")
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
