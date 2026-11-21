package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class OpenVpnRemote(val host: String, val port: Int, val network: String)

data class OpenVpnConfig(
    val remotes: List<OpenVpnRemote>,
    val ca: String,
    val clientCertificate: String?,
    val clientKey: String?,
    val controlWrapType: String?,
    val controlWrapKey: String?,
    val keyDirection: String?,
    val serverName: String?,
    val serverNameType: String?,
    val cipher: String?,
    val dataCiphers: List<String>,
    val dataCipherFallback: String?,
    val auth: String?,
    val tlsVersionMin: String?,
    val mtu: Int,
    val mssFix: Int?,
    val pingInterval: Int?,
    val pingRestart: Int?,
    val renegotiateSeconds: Int?,
    val explicitExitNotify: Int?,
    val remoteRandom: Boolean
) {
    fun toSingBoxEndpoint(tag: String = "ovpn-ep"): JSONObject {
        val tls = JSONObject()
            .put("certificate", JSONArray(ca.lines()))
            .put("remote_certificate_tls", "server")
        serverName?.let { tls.put("server_name", it) }
        serverNameType?.let { tls.put("server_name_type", it) }
        tlsVersionMin?.let { tls.put("version_min", it) }
        if (clientCertificate != null && clientKey != null) {
            tls.put("client_certificate", JSONArray(clientCertificate.lines()))
            tls.put("client_key", JSONArray(clientKey.lines()))
        }
        if (controlWrapType != null && controlWrapKey != null) {
            val wrap = JSONObject()
                .put("type", controlWrapType)
                .put("key", JSONArray(controlWrapKey.lines()))
            keyDirection?.let { wrap.put("direction", it) }
            tls.put("control_wrap", wrap)
        }
        val endpoint = JSONObject()
            .put("type", "openvpn-client")
            .put("tag", tag)
            .put("mode", "tls")
            .put("mtu", mtu)
            .put("tls", tls)
            .put("auth_retry", "none")
        if (remotes.size == 1) {
            endpoint.put("server", remotes.single().host)
                .put("server_port", remotes.single().port)
                .put("network", remotes.single().network)
        } else {
            endpoint.put("servers", JSONArray(remotes.map { remote ->
                JSONObject().put("server", remote.host)
                    .put("server_port", remote.port)
                    .put("network", remote.network)
            }))
            endpoint.put("remote_random", remoteRandom)
        }
        cipher?.let { endpoint.put("cipher", it) }
        if (dataCiphers.isNotEmpty()) endpoint.put("data_ciphers", JSONArray(dataCiphers))
        dataCipherFallback?.let { endpoint.put("data_ciphers_fallback", it) }
        auth?.let { endpoint.put("auth", it) }
        mssFix?.let { endpoint.put("mss_fix", it) }
        pingInterval?.let { endpoint.put("ping_interval", "${it}s") }
        pingRestart?.let { endpoint.put("ping_restart", "${it}s") }
        renegotiateSeconds?.let { endpoint.put("renegotiate_interval", "${it}s") }
        explicitExitNotify?.let { endpoint.put("explicit_exit_notify", it) }
        return endpoint
    }
}

/**
 * Strict, non-executing importer for a useful OpenVPN TLS profile subset.
 * External credential files, scripts, plugins, compression and unknown
 * directives are rejected rather than silently ignored.
 */
object OpenVpnConfigParser {
    private const val PREFIX = "openvpn-conf://"
    private val ignoredSafe = setOf(
        "client", "dev", "proto", "remote", "remote-random", "nobind",
        "persist-key", "persist-tun", "resolv-retry", "remote-cert-tls",
        "verify-x509-name", "auth", "cipher", "data-ciphers",
        "data-ciphers-fallback", "tls-version-min", "key-direction",
        "explicit-exit-notify", "ping", "ping-restart", "reneg-sec",
        "mssfix", "verb", "mute", "auth-nocache", "fast-io", "float",
        "remote-random-hostname", "pull", "route-nopull", "topology",
        "tun-mtu", "tun-mtu-extra", "sndbuf", "rcvbuf",
        "route-delay", "route", "route-ipv6", "redirect-gateway",
        "dhcp-option", "block-outside-dns", "register-dns", "setenv",
        "explicit-exit-notify"
    )
    private val supportedBlocks = setOf("ca", "cert", "key", "tls-auth", "tls-crypt")

    fun parse(text: String): OpenVpnConfig {
        require(text.length in 1..64_000) { "فایل OpenVPN خالی یا بیش از حد بزرگ است." }
        val directives = mutableListOf<Pair<String, List<String>>>()
        val blocks = linkedMapOf<String, String>()
        var activeBlock: String? = null
        val blockText = StringBuilder()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (activeBlock != null) {
                if (line.equals("</$activeBlock>", ignoreCase = true)) {
                    blocks[activeBlock!!] = blockText.toString().trim()
                    activeBlock = null
                    blockText.setLength(0)
                } else {
                    blockText.append(raw).append('\n')
                }
                return@forEachIndexed
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
            if (line.startsWith("<") && line.endsWith(">") && !line.startsWith("</")) {
                val name = line.substring(1, line.length - 1).trim().lowercase()
                require(name in supportedBlocks) { "بلوک OpenVPN ناشناخته یا پشتیبانی‌نشده در خط ${index + 1}: $name" }
                require(name !in blocks) { "بلوک OpenVPN تکراری است: $name" }
                activeBlock = name
                return@forEachIndexed
            }
            val tokens = Regex("""\"([^\"]*)\"|'([^']*)'|(\S+)""")
                .findAll(line).map { it.groups[1]?.value ?: it.groups[2]?.value ?: it.groups[3]!!.value }
                .toList()
            if (tokens.isEmpty()) return@forEachIndexed
            val key = tokens.first().lowercase()
            require(key in ignoredSafe) { "گزینه OpenVPN پشتیبانی‌نشده در خط ${index + 1}: $key" }
            if (key in setOf("setenv", "route", "route-ipv6", "dhcp-option", "redirect-gateway")) {
                // These options are not executed or copied; MLE owns the device route table.
                if (key == "setenv") require(tokens.drop(1).take(2) == listOf("opt", "block-outside-dns")) {
                    "فقط setenv opt block-outside-dns مجاز است."
                }
            }
            directives += key to tokens.drop(1)
        }
        require(activeBlock == null) { "بلوک inline OpenVPN بسته نشده است." }
        val values = directives.groupBy({ it.first }, { it.second })
        fun first(key: String): List<String>? = values[key]?.firstOrNull()
        fun scalar(key: String): String? = first(key)?.firstOrNull()
        require(values["client"] != null) { "فقط پروفایل‌های OpenVPN client پشتیبانی می‌شوند." }
        require(scalar("dev")?.startsWith("tun") == true) { "فقط OpenVPN با dev tun پشتیبانی می‌شود." }
        require(values["auth-user-pass"] == null) { "پروفایل به نام کاربری/رمز عبور جداگانه نیاز دارد؛ ورود تعاملی هنوز پیاده‌سازی نشده است." }
        require(values["compress"] == null && values["comp-lzo"] == null) { "فشرده‌سازی OpenVPN برای امنیت غیرفعال است." }
        require(values["pkcs12"] == null && values["secret"] == null) { "حالت‌های فایل PKCS12 و static-key پشتیبانی نمی‌شوند." }
        val remotes = values["remote"].orEmpty().map { args ->
            require(args.isNotEmpty()) { "دستور remote ناقص است." }
            val host = args[0]
            val explicitPort = args.getOrNull(1)?.toIntOrNull()
            val port = explicitPort ?: 1194
            val proto = (if (explicitPort == null) args.getOrNull(1) else args.getOrNull(2))
                ?: scalar("proto") ?: "udp"
            val network = when (proto.lowercase()) {
                "udp", "udp4", "udp6" -> "udp"
                "tcp", "tcp-client", "tcp4-client", "tcp6-client" -> "tcp"
                else -> error("پروتکل OpenVPN پشتیبانی نمی‌شود: $proto")
            }
            require(host.isNotBlank() && port in 1..65535) { "آدرس یا پورت remote معتبر نیست." }
            require(runCatching { URI("$network://$host:$port").host != null }.getOrDefault(false)) {
                "نام میزبان remote معتبر نیست."
            }
            OpenVpnRemote(host, port, network)
        }.distinct()
        require(remotes.isNotEmpty() && remotes.size <= 16) { "حداقل یک و حداکثر ۱۶ سرور remote لازم است." }
        val ca = blocks["ca"] ?: error("گواهی CA داخل فایل OpenVPN وجود ندارد؛ برای جلوگیری از غیرفعال‌کردن اعتبارسنجی TLS وارد نمی‌شود.")
        require(ca.contains("BEGIN CERTIFICATE") && ca.contains("END CERTIFICATE")) { "بلوک CA معتبر نیست." }
        val cert = blocks["cert"]
        val key = blocks["key"]
        require((cert == null) == (key == null)) { "گواهی کلاینت و کلید خصوصی باید با هم ارائه شوند." }
        if (cert != null) require(cert.contains("BEGIN CERTIFICATE") && cert.contains("END CERTIFICATE")) { "گواهی کلاینت معتبر نیست." }
        if (key != null) require(key.contains("PRIVATE KEY") && key.contains("END")) { "کلید خصوصی کلاینت معتبر نیست." }
        val tlsAuth = blocks["tls-auth"]
        val tlsCrypt = blocks["tls-crypt"]
        require(tlsAuth == null || tlsCrypt == null) { "tls-auth و tls-crypt هم‌زمان قابل استفاده نیستند." }
        val keyDirection = scalar("key-direction")?.let {
            when (it) { "0" -> "server"; "1" -> "client"; else -> error("key-direction باید 0 یا 1 باشد.") }
        }
        val tlsVersion = scalar("tls-version-min")?.removePrefix("or higher")
        require(tlsVersion == null || tlsVersion in setOf("1.0", "1.1", "1.2", "1.3")) { "نسخه TLS پشتیبانی نمی‌شود." }
        val verifyName = first("verify-x509-name")
        val serverName = verifyName?.firstOrNull()
        val serverNameType = verifyName?.getOrNull(1)?.let {
            when (it.lowercase()) {
                "name" -> "name"; "name-prefix" -> "name-prefix"; "subject" -> "subject"
                else -> error("نوع verify-x509-name پشتیبانی نمی‌شود.")
            }
        }
        require(scalar("remote-cert-tls") == null || scalar("remote-cert-tls") == "server") {
            "remote-cert-tls باید server باشد."
        }
        fun seconds(key: String): Int? {
            val rawValue = scalar(key) ?: return null
            val parsed = rawValue.toIntOrNull() ?: error("$key باید عدد صحیح باشد.")
            require(parsed in 1..86400) { "$key نامعتبر است." }
            return parsed
        }
        val mtuRaw = scalar("tun-mtu")
        val mtu = mtuRaw?.toIntOrNull() ?: if (mtuRaw.isNullOrBlank()) 1500 else error("tun-mtu باید عدد صحیح باشد.")
        require(mtu in 576..9000) { "tun-mtu باید بین 576 و 9000 باشد." }
        val mssRaw = scalar("mssfix")
        val mss = mssRaw?.toIntOrNull()
        if (!mssRaw.isNullOrBlank()) require(mss != null && mss in 536..9000) { "mssfix نامعتبر است." }
        val explicitRaw = scalar("explicit-exit-notify")
        val explicit = explicitRaw?.toIntOrNull()
        if (!explicitRaw.isNullOrBlank()) require(explicit != null && explicit in 0..10) { "explicit-exit-notify نامعتبر است." }
        val controlType = when {
            tlsCrypt != null -> "tls_crypt"
            tlsAuth != null -> "tls_auth"
            else -> null
        }
        return OpenVpnConfig(
            remotes = remotes,
            ca = ca,
            clientCertificate = cert,
            clientKey = key,
            controlWrapType = controlType,
            controlWrapKey = tlsCrypt ?: tlsAuth,
            keyDirection = if (controlType == "tls_auth") keyDirection else null,
            serverName = serverName,
            serverNameType = serverNameType,
            cipher = scalar("cipher"),
            dataCiphers = scalar("data-ciphers")?.split(':')?.filter(String::isNotBlank).orEmpty(),
            dataCipherFallback = scalar("data-ciphers-fallback"),
            auth = scalar("auth"),
            tlsVersionMin = tlsVersion,
            mtu = mtu,
            mssFix = mss,
            pingInterval = seconds("ping"),
            pingRestart = seconds("ping-restart"),
            renegotiateSeconds = seconds("reneg-sec"),
            explicitExitNotify = explicit,
            remoteRandom = values["remote-random"] != null
        )
    }

    fun encodeForStorage(text: String): String =
        PREFIX + Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decodeStored(value: String): OpenVpnConfig {
        require(value.startsWith(PREFIX)) { "OpenVPN profile encoding is invalid." }
        val raw = Base64.decode(value.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return parse(raw.toString(Charsets.UTF_8))
    }
}
