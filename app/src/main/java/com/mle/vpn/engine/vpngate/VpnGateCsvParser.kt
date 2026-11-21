package com.mle.vpn.engine.vpngate

import java.util.Base64

/**
 * Parser for the public VPN Gate 15-column CSV. Malformed rows are skipped independently.
 * The response may contain public inline .ovpn certificates; these remain in memory only.
 */
object VpnGateCsvParser {
    private const val COLUMN_COUNT = 15
    private const val MAX_PROFILE_CHARS = 64_000
    private val remotePattern = Regex("""^\\s*remote\\s+(\\S+)\\s+(\\d+)(?:\\s+.*)?$""", RegexOption.MULTILINE)
    private val hostPattern = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")
    private val ipv4Pattern = Regex("""(?:\\d{1,3}\\.){3}\\d{1,3}""")

    fun parse(csv: String): List<VpnGateServer> {
        if (csv.length > 5_000_000) return emptyList()
        val result = LinkedHashMap<String, VpnGateServer>()
        csv.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("*")) return@forEach
            val columns = line.split(',')
            if (columns.size != COLUMN_COUNT) return@forEach

            val hostName = columns[0].trim()
            val ip = columns[1].trim()
            val countryCode = columns[6].trim().uppercase()
            if (!hostPattern.matches(hostName) || !ipv4Pattern.matches(ip)) return@forEach
            if (ip.split('.').any { it.toIntOrNull() !in 0..255 }) return@forEach
            if (countryCode.length != 2 || countryCode.any { it !in 'A'..'Z' }) return@forEach

            val encoded = columns[14].trim()
            if (encoded.isEmpty() || encoded.length > MAX_PROFILE_CHARS * 2) return@forEach
            val profile = runCatching {
                String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
            }.getOrNull()?.takeIf { it.isNotBlank() && it.length <= MAX_PROFILE_CHARS }
                ?: return@forEach
            val remote = remotePattern.find(profile) ?: return@forEach
            val remoteHost = remote.groupValues[1].trim('[', ']')
            val remotePort = remote.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 }
                ?: return@forEach
            if (remoteHost.isBlank() || remoteHost.length > 253) return@forEach

            result.putIfAbsent(hostName, VpnGateServer(
                hostName = hostName,
                ip = ip,
                score = columns[2].trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                pingMs = columns[3].trim().toIntOrNull()?.takeIf { it >= 0 } ?: -1,
                speedBytesPerSecond = columns[4].trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                country = columns[5].trim().take(80),
                countryCode = countryCode,
                sessions = columns[7].trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                uptimeMs = columns[8].trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                openVpnConfig = profile,
                remoteHost = remoteHost,
                remotePort = remotePort
            ))
        }
        return result.values.toList()
    }
}
