package com.mle.vpn.profile

import android.util.Base64
import java.net.URI

data class OpenVpnRemote(val host: String, val port: Int, val network: String)

data class OpenVpnConfig(
    val remotes: List<OpenVpnRemote>,
    val ca: String,
    val clientCertificate: String?,
    val clientKey: String?,
    val username: String?,
    val password: String?,
    val controlWrapType: String?,
    val controlWrapKey: String?,
    val controlWrapDirection: String?,
    val serverName: String?,
    val serverNameType: String?,
    val dataCiphers: List<String>,
    val fallbackCipher: String?,
    val authDigest: String?,
    val mtu: Int,
    val redirectGateway: Boolean
)

/**
 * Strict parser for portable OpenVPN client profiles.
 * Only inline credentials/certificates and a conservative TLS client subset
 * are accepted. External file paths, scripts, plugins, proxies and compression
 * are rejected rather than silently ignored.
 */
object OpenVpnConfigParser {
    private const val PREFIX = "openvpn-conf://"
    private const val MAX_BYTES = 128 * 1024
    private val allowedHost = Regex("(?i)^[a-z0-9.-]+$")

    fun parse(text: String): OpenVpnConfig {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "OpenVPN profile is too large." }
        val directives = linkedMapOf<String, MutableList<List<String>>>()
        val blocks = linkedMapOf<String, String>()
        var blockName: String? = null
        val blockContent = StringBuilder()

        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (blockName != null) {
                if (line.equals("</$blockName>", ignoreCase = true)) {
                    blocks[blockName!!] = blockContent.toString().trim()
                    blockName = null
                    blockContent.setLength(0)
                } else {
                    blockContent.append(raw).append('\n')
                }
                return@forEachIndexed
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
            if (line.startsWith("<") && line.endsWith(">") && !line.startsWith("</")) {
                val name = line.substring(1, line.length - 1).lowercase()
                require(name in setOf("ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2", "auth-user-pass")) {
                    "Unsupported inline OpenVPN block <$name>."
                }
                require(name !in blocks) { "Duplicate inline OpenVPN block <$name>." }
                blockName = name
                return@forEachIndexed
            }
            val tokens = Regex("\\\"([^\\\"]*)\\\"|'([^']*)'|(\\S+)").findAll(line)
                .map { it.groups[1]?.value ?: it.groups[2]?.value ?: it.groups[3]?.value.orEmpty() }
                .toList()
            require(tokens.isNotEmpty()) { "Invalid OpenVPN directive at line ${index + 1}." }
            val key = tokens.first().lowercase()
            directives.getOrPut(key) { mutableListOf() }.add(tokens.drop(1))
        }
        require(blockName == null) { "Unterminated inline OpenVPN block <$blockName>." }
        require(directives.keys.none { it in FORBIDDEN_DIRECTIVES }) {
            "This OpenVPN profile contains scripts, external files, proxies or compression that are not supported."
        }
        val allowed = setOf(
            "client", "dev", "proto", "remote", "remote-random", "resolv-retry", "nobind",
            "persist-key", "persist-tun", "remote-cert-tls", "verify-x509-name", "tls-version-min",
            "cipher", "data-ciphers", "auth", "auth-nocache", "verb", "mute", "pull",
            "redirect-gateway", "key-direction", "explicit-exit-notify", "auth-user-pass",
            "ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2", "setenv"
        )
        require(directives.keys.all { it in allowed }) {
            "Unsupported OpenVPN directive: ${directives.keys.first { it !in allowed }}."
        }
        require(directives.containsKey("client")) { "Only OpenVPN client profiles are accepted." }
        require(directives["dev"]?.firstOrNull()?.firstOrNull() == "tun") { "OpenVPN profile must use dev tun." }
        val remotes = directives["remote"].orEmpty().map { args ->
            require(args.isNotEmpty() && args.size <= 3) { "Invalid OpenVPN remote directive." }
            val host = args[0].removeSurrounding("[", "]")
            require(host.length <= 253 && allowedHost.matches(host) && !host.contains("..")) { "Invalid OpenVPN remote host." }
            val port = args.getOrNull(1)?.toIntOrNull() ?: 1194
            require(port in 1..65535) { "Invalid OpenVPN remote port." }
            val network = when (args.getOrNull(2)?.lowercase() ?: directives["proto"]?.firstOrNull()?.firstOrNull()?.lowercase() ?: "udp") {
                "udp" -> "udp"
                "tcp", "tcp-client" -> "tcp"
                else -> error("Unsupported OpenVPN transport.")
            }
            OpenVpnRemote(host, port, network)
        }
        require(remotes.isNotEmpty() && remotes.size <= 8) { "OpenVPN profile must contain 1 to 8 remote servers." }
        val network = remotes.first().network
        require(remotes.all { it.network == network }) { "Mixed TCP and UDP remotes are not supported in one profile." }
        val ca = blocks["ca"] ?: error("Inline <ca> certificate is required.")
        require(ca.contains("BEGIN CERTIFICATE") && ca.contains("END CERTIFICATE")) { "Invalid inline CA certificate." }
        val cert = blocks["cert"] ?: directives["cert"]?.firstOrNull()?.firstOrNull()?.let { error("External cert paths are not supported: $it") }
        val key = blocks["key"] ?: directives["key"]?.firstOrNull()?.firstOrNull()?.let { error("External key paths are not supported: $it") }
        require((cert == null) == (key == null)) { "Client certificate and private key must be supplied together." }
        if (cert != null) require(cert.contains("BEGIN CERTIFICATE") && cert.contains("END CERTIFICATE")) { "Invalid inline client certificate." }
        if (key != null) require(key.contains("PRIVATE KEY")) { "Invalid inline client private key." }

        val authBlock = blocks["auth-user-pass"]
        val credentials = authBlock?.lineSequence()?.map(String::trim)?.filter(String::isNotEmpty)?.toList()
        require(credentials == null || credentials.size == 2) { "Inline auth-user-pass must contain exactly username and password." }
        if (directives.containsKey("auth-user-pass")) {
            require(authBlock != null || directives["auth-user-pass"]?.firstOrNull().isNullOrEmpty()) {
                "External auth-user-pass files are not supported; embed credentials in <auth-user-pass>."
            }
            require(authBlock != null) { "This profile needs username/password; embed them in <auth-user-pass>." }
        }

        val wrapEntries = listOf("tls-auth", "tls-crypt", "tls-crypt-v2").filter { blocks.containsKey(it) || directives.containsKey(it) }
        require(wrapEntries.size <= 1) { "Only one OpenVPN control-channel wrapping mode is supported." }
        val wrapType = wrapEntries.firstOrNull()?.replace('-', '_')
        val wrapKey = wrapEntries.firstOrNull()?.let { name ->
            blocks[name] ?: error("External $name key files are not supported.")
        }
        val directionRaw = directives["key-direction"]?.firstOrNull()?.firstOrNull()
        val direction = directionRaw?.let {
            when (it) { "0" -> "server"; "1" -> "client"; else -> error("Invalid OpenVPN key-direction.") }
        }
        if (wrapType == "tls_auth") require(direction != null) { "tls-auth requires key-direction 0 or 1." }
        val verify = directives["verify-x509-name"]?.firstOrNull()
        val serverName = verify?.firstOrNull()
        val serverNameType = verify?.getOrNull(1)?.lowercase()?.takeIf { it in setOf("name", "subject", "name-prefix") }
        require(verify == null || serverNameType != null || verify.size == 1) { "Unsupported verify-x509-name mode." }
        val ciphers = directives["data-ciphers"]?.firstOrNull()?.joinToString("")?.split(':')?.filter(String::isNotBlank).orEmpty()
        require(ciphers.size <= 16 && ciphers.all { it.matches(Regex("[A-Za-z0-9_-]{3,48}")) }) { "Invalid OpenVPN data-ciphers list." }
        val mtu = directives["tun-mtu"]?.firstOrNull()?.firstOrNull()?.toIntOrNull() ?: 1500
        require(mtu in 576..9000) { "OpenVPN MTU is out of range." }
        val redirect = directives.containsKey("redirect-gateway")
        return OpenVpnConfig(
            remotes, ca, cert, key, credentials?.getOrNull(0), credentials?.getOrNull(1),
            wrapType, wrapKey, direction, serverName, serverNameType,
            ciphers, directives["cipher"]?.firstOrNull()?.firstOrNull(),
            directives["auth"]?.firstOrNull()?.firstOrNull(), mtu, redirect
        )
    }

    fun encodeForStorage(text: String): String =
        PREFIX + Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decodeStored(value: String): OpenVpnConfig {
        require(value.startsWith(PREFIX)) { "OpenVPN profile encoding is invalid." }
        val raw = Base64.decode(value.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return parse(raw.toString(Charsets.UTF_8))
    }

    private val FORBIDDEN_DIRECTIVES = setOf(
        "up", "down", "route-up", "ipchange", "plugin", "script-security", "learn-address",
        "client-connect", "client-disconnect", "http-proxy", "socks-proxy", "compress", "comp-lzo",
        "allow-compression", "auth-user-pass-verify", "management", "config", "include", "askpass"
    )
}
