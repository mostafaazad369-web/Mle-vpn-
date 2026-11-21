package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class ProfileProtocol {
    VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA, HYSTERIA2,
    TUIC, ANYTLS, WIREGUARD, OPENVPN, SOCKS, HTTP, UNKNOWN
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

    /** Imports a single wg-quick .conf profile. Never logs or exposes the private key. */
    fun parseWireGuardConfig(input: String): ProfileParseResult = try {
        val data = WireGuardConfigParser.parse(input)
        ProfileParseResult.Success(
            ImportedProfile(
                id = java.util.UUID.randomUUID().toString(),
                protocol = ProfileProtocol.WIREGUARD,
                displayName = "WireGuard • ${data.peerHost}",
                host = data.peerHost,
                port = data.peerPort,
                sourceUri = input
            )
        )
    } catch (error: Exception) {
        ProfileParseResult.Failure(error.message ?: "فایل WireGuard معتبر نیست.")
    }

    /** Imports a self-contained .ovpn profile after conservative directive and TLS preflight. */
    fun parseOpenVpnConfig(input: String): ProfileParseResult = try {
        val summary = OpenVpnProfilePreflight.inspect(input)
        val remote = summary.remotes.first()
        ProfileParseResult.Success(
            ImportedProfile(
                id = java.util.UUID.randomUUID().toString(),
                protocol = ProfileProtocol.OPENVPN,
                displayName = "OpenVPN • ${remote.host}",
                host = remote.host,
                port = remote.port,
                sourceUri = input
            )
        )
    } catch (error: Exception) {
        ProfileParseResult.Failure(error.message ?: "OpenVPN profile is not supported.")
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

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}
