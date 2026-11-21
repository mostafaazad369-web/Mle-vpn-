package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class ProfileProtocol {
    VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA, HYSTERIA2,
    TUIC, ANYTLS, WIREGUARD, SOCKS, HTTP, UNKNOWN
}

data class ImportedProfile(
    val id: String,
    val protocol: ProfileProtocol,
    val displayName: String,
    val host: String,
    val port: Int,
    /** Original URI is sensitive. Keep it in encrypted storage and never log it. */
    val sourceUri: String
)

data class ProfileBatchResult(
    val profiles: List<ImportedProfile>,
    val rejectedCount: Int,
    val duplicateCount: Int
)

sealed interface ProfileParseResult {
    data class Success(val profile: ImportedProfile) : ProfileParseResult
    data class Failure(val reason: String) : ProfileParseResult
}

/**
 * Safe first-pass URI classifier for common sing-box share links.
 * This validates basic shape only; it does not prove that a node is reachable
 * or that every optional transport parameter is supported by the installed core.
 */
object ProfileImporter {
    private val schemes = mapOf(
        "vless" to ProfileProtocol.VLESS,
        "vmess" to ProfileProtocol.VMESS,
        "trojan" to ProfileProtocol.TROJAN,
        "ss" to ProfileProtocol.SHADOWSOCKS,
        "hysteria" to ProfileProtocol.HYSTERIA,
        "hysteria2" to ProfileProtocol.HYSTERIA2,
        "hy2" to ProfileProtocol.HYSTERIA2,
        "tuic" to ProfileProtocol.TUIC,
        "anytls" to ProfileProtocol.ANYTLS,
        "wireguard" to ProfileProtocol.WIREGUARD,
        "socks" to ProfileProtocol.SOCKS,
        "http" to ProfileProtocol.HTTP
    )

    /**
     * Parses a pasted multi-line or Base64 subscription. Invalid entries are
     * skipped and counted; duplicate source links are removed before storage.
     */
    fun parseBatch(input: String): ProfileBatchResult {
        require(input.length <= 1_000_000) { "فهرست کانفیگ بیش از حد بزرگ است." }
        val raw = input.trim()
        if (raw.startsWith("[Interface]", ignoreCase = true)) {
            return runCatching { ProfileBatchResult(listOf(parseWireGuardConfig(raw)), 0, 0) }
                .getOrElse { ProfileBatchResult(emptyList(), 1, 0) }
        }
        val decoded = if (raw.contains("://")) raw else {
            runCatching {
                String(Base64.decode(raw, Base64.DEFAULT), StandardCharsets.UTF_8)
            }.getOrNull()?.takeIf { it.contains("://") } ?: raw
        }
        val lines = decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .take(501)
            .toList()
        require(lines.size <= 500) { "حداکثر ۵۰۰ کانفیگ در هر بار قابل ورود است." }
        val accepted = LinkedHashMap<String, ImportedProfile>()
        var rejected = 0
        var duplicates = 0
        lines.forEach { line ->
            when (val result = parse(line)) {
                is ProfileParseResult.Success -> {
                    if (accepted.putIfAbsent(result.profile.sourceUri, result.profile) != null) duplicates++
                }
                is ProfileParseResult.Failure -> rejected++
            }
        }
        return ProfileBatchResult(accepted.values.toList(), rejected, duplicates)
    }

    fun parse(input: String): ProfileParseResult {
        val raw = input.trim()
        if (raw.isEmpty() || raw.length > 64_000) {
            return ProfileParseResult.Failure("لینک خالی یا بیش از حد طولانی است.")
        }
        if (raw.startsWith("vmess://", ignoreCase = true)) return parseVmess(raw)
        if (raw.startsWith("ss://", ignoreCase = true)) return parseShadowsocks(raw)

        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()
                ?: return ProfileParseResult.Failure("نوع لینک مشخص نیست.")
            val protocol = schemes[scheme]
                ?: return ProfileParseResult.Failure("این نوع کانفیگ هنوز پشتیبانی نمی‌شود.")
            if (protocol == ProfileProtocol.WIREGUARD) {
                return ProfileParseResult.Failure("برای WireGuard فایل تنظیمات [Interface]/[Peer] را وارد کنید.")
            }
            val host = uri.host ?: return ProfileParseResult.Failure("آدرس سرور در لینک وجود ندارد.")
            val port = uri.port
            if (port !in 1..65535) return ProfileParseResult.Failure("پورت سرور معتبر نیست.")
            val fragment = uri.rawFragment?.let { decode(it) }?.take(80)
            val display = fragment?.takeIf { it.isNotBlank() } ?: "$protocol • $host"
            ProfileParseResult.Success(
                ImportedProfile(
                    id = java.util.UUID.randomUUID().toString(),
                    protocol = protocol,
                    displayName = display,
                    host = host,
                    port = port,
                    sourceUri = raw
                )
            )
        } catch (_: Exception) {
            ProfileParseResult.Failure("ساختار لینک کانفیگ قابل خواندن نیست.")
        }
    }

    private fun parseShadowsocks(raw: String): ProfileParseResult = try {
        val parsed = ShadowsocksLinkParser.parse(raw)
        ProfileParseResult.Success(
            ImportedProfile(
                id = java.util.UUID.randomUUID().toString(),
                protocol = ProfileProtocol.SHADOWSOCKS,
                displayName = parsed.name.take(80),
                host = parsed.host,
                port = parsed.port,
                sourceUri = raw
            )
        )
    } catch (error: Exception) {
        ProfileParseResult.Failure(error.message ?: "لینک Shadowsocks معتبر نیست.")
    }

    private fun parseVmess(raw: String): ProfileParseResult {
        return try {
            val encoded = raw.substringAfter("://").substringBefore('#').trim()
            val jsonText = String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
            val json = JSONObject(jsonText)
            val host = json.optString("add").takeIf { it.isNotBlank() }
                ?: return ProfileParseResult.Failure("آدرس سرور VMess وجود ندارد.")
            val port = json.optInt("port", -1)
            if (port !in 1..65535) return ProfileParseResult.Failure("پورت VMess معتبر نیست.")
            val name = raw.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode)
                ?: json.optString("ps").takeIf { it.isNotBlank() }
                ?: "VMess • $host"
            ProfileParseResult.Success(
                ImportedProfile(
                    id = java.util.UUID.randomUUID().toString(),
                    protocol = ProfileProtocol.VMESS,
                    displayName = name.take(80),
                    host = host,
                    port = port,
                    sourceUri = raw
                )
            )
        } catch (_: Exception) {
            ProfileParseResult.Failure("لینک VMess معتبر نیست یا JSON آن قابل خواندن نیست.")
        }
    }


    /**
     * Imports a standard WireGuard .conf document. AmneziaWG obfuscation
     * directives are rejected until an AmneziaWG-capable runtime is bundled.
     */
    private fun parseWireGuardConfig(raw: String): ImportedProfile {
        require(raw.length <= 64_000) { "فایل WireGuard بیش از حد بزرگ است." }
        require(raw.lineSequence().count { it.trim().equals("[Interface]", true) } == 1) {
            "فایل WireGuard باید دقیقاً یک بخش [Interface] داشته باشد."
        }
        require(raw.lineSequence().count { it.trim().equals("[Peer]", true) } == 1) {
            "فعلاً فایل WireGuard باید دقیقاً یک بخش [Peer] داشته باشد؛ فایل چند Peer پشتیبانی نمی‌شود."
        }
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var section: String? = null
        raw.lineSequence().forEach { original ->
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
        val unsupportedAwg = setOf("jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4")
        require(unsupportedAwg.none { it in iface || it in peer }) {
            "این فایل شامل پارامترهای مبهم‌سازی AmneziaWG است؛ موتور AmneziaWG هنوز به MLE متصل نشده است."
        }
        fun validKey(value: String): Boolean = runCatching {
            android.util.Base64.decode(value, android.util.Base64.DEFAULT).size == 32
        }.getOrDefault(false)
        require(validKey(iface["privatekey"].orEmpty())) { "کلید خصوصی WireGuard معتبر نیست." }
        require(validKey(peer["publickey"].orEmpty())) { "کلید عمومی Peer معتبر نیست." }
        val addresses = iface["address"].orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
        require(addresses.isNotEmpty() && addresses.all { '/' in it }) { "آدرس محلی WireGuard باید شامل CIDR باشد." }
        val endpoint = java.net.URI("udp://${peer["endpoint"].orEmpty()}")
        val host = endpoint.host ?: error("آدرس Endpoint در فایل WireGuard معتبر نیست.")
        val port = endpoint.port
        require(port in 1..65535) { "پورت Endpoint در فایل WireGuard معتبر نیست." }
        val allowed = peer["allowedips"].orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
        require(allowed.isNotEmpty() && allowed.all { '/' in it }) { "AllowedIPs در فایل WireGuard معتبر نیست." }
        val keepaliveRaw = peer["persistentkeepalive"]
        if (!keepaliveRaw.isNullOrBlank()) {
            val keepalive = keepaliveRaw.toIntOrNull() ?: error("PersistentKeepalive باید عدد صحیح باشد.")
            require(keepalive in 0..65535) { "PersistentKeepalive معتبر نیست." }
        }
        val mtuRaw = iface["mtu"]
        if (!mtuRaw.isNullOrBlank()) {
            val mtu = mtuRaw.toIntOrNull() ?: error("MTU باید عدد صحیح باشد.")
            require(mtu in 1280..9000) { "MTU باید بین ۱۲۸۰ و ۹۰۰۰ باشد." }
        }
        peer["reserved"]?.takeIf(String::isNotBlank)?.let { rawReserved ->
            val bytes = rawReserved.split(',').mapNotNull { it.trim().toIntOrNull() }
            require(bytes.size == 3 && bytes.all { it in 0..255 }) { "Reserved باید دقیقاً سه عدد بین صفر تا ۲۵۵ باشد." }
        }
        return ImportedProfile(
            id = java.util.UUID.randomUUID().toString(),
            protocol = ProfileProtocol.WIREGUARD,
            displayName = "WireGuard • ${addresses.first()}",
            host = host,
            port = port,
            sourceUri = raw.trim()
        )
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}
