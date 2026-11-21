package com.mle.vpn.profile

import android.util.Base64
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

data class AmneziaWgProfileConfig(
    val host: String,
    val port: Int,
    val rawConfig: String
)

/**
 * Validation and encrypted-vault transport for official AmneziaWG .conf files.
 * The official tunnel module performs the authoritative config parse; this wrapper
 * only extracts the first peer endpoint for profile listing and diagnostics.
 */
object AmneziaWgConfigParser {
    private val endpointLine = Regex(
        """(?im)^\s*Endpoint\s*=\s*(\[[^]]+]|[^\s:]+):(\d+)\s*$"""
    )

    fun parse(raw: String): AmneziaWgProfileConfig {
        require(raw.isNotBlank() && raw.length <= 64 * 1024) {
            "فایل AmneziaWG خالی یا بزرگ‌تر از ۶۴ کیلوبایت است."
        }
        val parsed = Config.parse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        require(parsed.peers.isNotEmpty()) { "پروفایل AmneziaWG هیچ Peer ندارد." }
        val match = endpointLine.find(raw)
            ?: error("آدرس Endpoint در پروفایل AmneziaWG پیدا نشد.")
        val host = match.groupValues[1].removePrefix("[").removeSuffix("]")
        val port = match.groupValues[2].toIntOrNull()
            ?: error("پورت Endpoint معتبر نیست.")
        require(host.isNotBlank() && port in 1..65535) { "Endpoint پروفایل AmneziaWG معتبر نیست." }
        return AmneziaWgProfileConfig(host, port, raw.trim())
    }

    fun encodeForStorage(raw: String): String {
        val validated = parse(raw)
        return PREFIX + Base64.encodeToString(
            validated.rawConfig.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
    }

    fun decodeStored(value: String): String {
        require(value.startsWith(PREFIX)) { "ذخیره‌سازی پروفایل AmneziaWG معتبر نیست." }
        val encoded = value.removePrefix(PREFIX)
        val raw = Base64.decode(encoded, Base64.NO_WRAP).toString(Charsets.UTF_8)
        return parse(raw).rawConfig
    }

    fun isAmneziaConfig(value: String): Boolean =
        Regex("""(?im)^\s*(?:Jc|Jmin|Jmax|S1|S2|H[1-4]|I[1-5])\s*=""")
            .containsMatchIn(value)

    private const val PREFIX = "awg-conf://"
}
