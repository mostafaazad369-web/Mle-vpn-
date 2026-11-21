package com.mle.vpn.profile

import java.security.cert.CertificateFactory

/**
 * Conservative OpenVPN .ovpn preflight.
 *
 * This is deliberately not a runtime or an importer: it accepts only self-contained
 * inline material, returns non-secret metadata, and refuses directives it cannot
 * preserve. The original profile must be stored only in the encrypted profile vault.
 */
data class OpenVpnRemote(val host: String, val port: Int, val protocol: String)

data class OpenVpnProfileSummary(
    val remotes: List<OpenVpnRemote>,
    val hasCa: Boolean,
    val hasClientCertificate: Boolean,
    val hasPrivateKey: Boolean,
    val hasInlineCredentials: Boolean,
    val usesPeerFingerprint: Boolean
)

object OpenVpnProfilePreflight {
    const val MAX_BYTES = 1_048_576

    private val inlineBlocks = setOf("ca", "cert", "key", "tls-auth", "tls-crypt", "extra-certs", "auth-user-pass")
    private val allowed = setOf(
        "client", "dev", "dev-type", "proto", "remote", "port", "rport", "nobind",
        "remote-cert-tls", "verify-x509-name", "peer-fingerprint", "persist-key", "persist-tun",
        "reneg-sec", "dhcp-option", "redirect-gateway", "redirect-private", "route", "route-ipv6",
        "route-metric", "route-nopull", "route-delay", "route-gateway", "verb", "mute",
        "auth-user-pass", "auth-nocache", "data-ciphers", "cipher", "data-ciphers-fallback",
        "auth", "tls-version-min", "tls-version-max", "tls-cipher", "tls-ciphersuites",
        "tls-client", "key-direction", "resolv-retry", "connect-retry", "connect-retry-max",
        "connect-timeout", "server-poll-timeout", "remote-random", "remote-random-hostname",
        "explicit-exit-notify", "tun-mtu", "mssfix", "sndbuf", "rcvbuf", "ping",
        "ping-restart", "keepalive", "pull", "pull-filter", "topology", "ifconfig",
        "ifconfig-ipv6", "auth-retry"
    )

    fun inspect(text: String): OpenVpnProfileSummary {
        require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) {
            "OpenVPN profile is empty or exceeds 1 MiB."
        }
        require('\u0000' !in text) { "OpenVPN profile contains a NUL byte." }

        val remotes = mutableListOf<OpenVpnRemote>()
        var defaultPort = 1194
        var defaultProtocol = "udp"
        var client = false
        var tun = false
        var remoteCertServer = false
        var peerFingerprint = false
        var hasCa = false
        var hasCert = false
        var hasKey = false
        var inlineCredentials = false
        var block: String? = null
        val blockLines = mutableListOf<String>()

        fun finishBlock(tag: String) {
            val value = blockLines.joinToString("\n").trim()
            require(value.isNotEmpty()) { "OpenVPN <$tag> block is empty." }
            when (tag) {
                "ca" -> {
                    try {
                        val certs = CertificateFactory.getInstance("X.509").generateCertificates(value.byteInputStream())
                        require(certs.isNotEmpty()) { "No CA certificate found." }
                    } catch (_: Exception) {
                        throw IllegalArgumentException("OpenVPN CA certificate is invalid.")
                    }
                    hasCa = true
                }
                "cert" -> hasCert = true
                "key" -> hasKey = true
                "auth-user-pass" -> {
                    val lines = value.lineSequence().filter { it.isNotBlank() }.toList()
                    require(lines.size in 1..2) { "Inline OpenVPN credentials are malformed." }
                    inlineCredentials = true
                }
            }
            blockLines.clear()
        }

        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (block != null) {
                if (line == "</$block>") {
                    finishBlock(block!!)
                    block = null
                } else {
                    require(!line.startsWith("<")) { "Nested OpenVPN inline blocks are not supported (line ${index + 1})." }
                    blockLines += raw
                }
                return@forEachIndexed
            }
            if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
            if (line.startsWith("<")) {
                val tag = line.removePrefix("<").removeSuffix(">")
                require(line == "<$tag>" && tag in inlineBlocks) {
                    "Unsupported OpenVPN inline block on line ${index + 1}."
                }
                block = tag
                return@forEachIndexed
            }
            val args = tokenize(line)
            if (args.isEmpty()) return@forEachIndexed
            val key = args.first().removePrefix("--").lowercase()
            require(key !in FORBIDDEN && key in allowed) {
                "Unsupported or unsafe OpenVPN directive '$key' on line ${index + 1}."
            }
            when (key) {
                "client", "tls-client" -> {
                    require(args.size == 1) { "Malformed '$key' directive." }
                    client = true
                }
                "dev" -> {
                    require(args.size == 2 && args[1].startsWith("tun")) { "Only OpenVPN TUN devices are supported." }
                    tun = true
                }
                "dev-type" -> require(args.getOrNull(1) == "tun") { "Only OpenVPN TUN devices are supported." }
                "proto" -> {
                    defaultProtocol = args.getOrNull(1) ?: error("Missing OpenVPN protocol.")
                    require(defaultProtocol in SUPPORTED_PROTOCOLS) { "Unsupported OpenVPN transport." }
                }
                "port", "rport" -> {
                    defaultPort = args.getOrNull(1)?.toIntOrNull() ?: error("Invalid OpenVPN port.")
                    require(defaultPort in 1..65535) { "Invalid OpenVPN port." }
                }
                "remote" -> {
                    require(args.size in 2..4) { "Malformed OpenVPN remote directive." }
                    val host = args[1]
                    val port = args.getOrNull(2)?.toIntOrNull() ?: defaultPort
                    val proto = args.getOrNull(3) ?: defaultProtocol
                    require(host.length in 1..253 && host.none { it.isWhitespace() || it in "/\\[]" }) {
                        "Invalid OpenVPN remote host."
                    }
                    require(port in 1..65535 && proto in SUPPORTED_PROTOCOLS) {
                        "Invalid OpenVPN remote port or transport."
                    }
                    remotes += OpenVpnRemote(host, port, proto)
                }
                "remote-cert-tls" -> {
                    require(args.getOrNull(1) == "server") { "OpenVPN must verify a server certificate." }
                    remoteCertServer = true
                }
                "peer-fingerprint" -> {
                    val fp = args.getOrNull(1) ?: error("Missing OpenVPN peer fingerprint.")
                    require(fp.matches(Regex("(?i)SHA256:([0-9a-f]{2}:){31}[0-9a-f]{2}"))) {
                        "Invalid OpenVPN SHA-256 peer fingerprint."
                    }
                    peerFingerprint = true
                }
                "auth-user-pass" -> require(args.size == 1) {
                    "External OpenVPN credential files are not accepted; use app-managed credentials."
                }
                "tls-auth", "tls-crypt", "ca", "cert", "key", "extra-certs" -> {
                    // File-path forms are intentionally rejected until companion-file import exists.
                    error("OpenVPN '$key' must be supplied as an inline block.")
                }
                else -> require(args.size >= 1) { "Malformed OpenVPN directive '$key'." }
            }
        }

        require(block == null) { "OpenVPN inline block <$block> is not closed." }
        require(client && tun) { "OpenVPN profile must declare client mode and a TUN device." }
        require(remotes.isNotEmpty()) { "OpenVPN profile has no remote server." }
        require(hasCa || peerFingerprint) { "OpenVPN profile needs an inline CA or a SHA-256 peer fingerprint." }
        require(remoteCertServer || peerFingerprint) { "OpenVPN server certificate verification is required." }
        require(!hasKey || hasCert) { "OpenVPN private key was supplied without a client certificate." }

        return OpenVpnProfileSummary(
            remotes = remotes.distinct(),
            hasCa = hasCa,
            hasClientCertificate = hasCert,
            hasPrivateKey = hasKey,
            hasInlineCredentials = inlineCredentials,
            usesPeerFingerprint = peerFingerprint
        )
    }

    private fun tokenize(line: String): List<String> {
        val result = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (c in line) {
            if (escaped) { word.append(c); escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (quote != null) { if (c == quote) quote = null else word.append(c); continue }
            if (c == '\'' || c == '"') { quote = c; continue }
            if ((c == '#' || c == ';') && word.isEmpty()) break
            if (c.isWhitespace()) {
                if (word.isNotEmpty()) { result += word.toString(); word.clear() }
            } else word.append(c)
        }
        require(quote == null && !escaped) { "Invalid quoting in OpenVPN profile." }
        if (word.isNotEmpty()) result += word.toString()
        return result
    }

    private val SUPPORTED_PROTOCOLS = setOf("udp", "udp4", "udp6", "tcp-client", "tcp4-client", "tcp6-client")
    private val FORBIDDEN = setOf(
        "up", "down", "route-up", "route-pre-down", "ipchange", "plugin", "management",
        "config", "daemon", "cd", "chroot", "setenv", "setenv-safe", "script-security",
        "learn-address", "client-connect", "client-disconnect", "auth-user-pass-verify",
        "tls-verify", "tmp-dir", "writepid", "log", "log-append", "status", "status-version",
        "comp-lzo", "compress", "allow-compression", "fragment", "http-proxy", "socks-proxy"
    )
}
