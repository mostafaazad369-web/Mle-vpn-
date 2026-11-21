package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class ProfileProtocol {
    VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA, HYSTERIA2,
    TUIC, ANYTLS, WIREGUARD, WARP, OPENVPN, SOCKS, HTTP, UNKNOWN
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
    /** Cloudflare WARP WireGuard peer public key, used only to identify imported configs. */
    const val WARP_PEER_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
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
        if (looksLikeOpenVpn(raw)) {
            return when (val parsed = parse(raw)) {
                is ProfileParseResult.Success -> ProfileBatchResult(listOf(parsed.profile), 0, 0)
                is ProfileParseResult.Failure -> ProfileBatchResult(emptyList(), 1, 0)
            }
        }
        if (raw.contains("[Interface]", ignoreCase = true)) {
            return when (val parsed = parse(raw)) {
                is ProfileParseResult.Success -> ProfileBatchResult(listOf(parsed.profile), 0, 0)
                is ProfileParseResult.Failure -> ProfileBatchResult(emptyList(), 1, 0)
            }
        }

        // Some public subscription feeds are Base64-wrapped twice: the whole
        // document decodes to metadata plus individual Base64 payload lines.
        val decodedDocument = if (raw.contains("://")) raw else {
            decodeBase64Text(raw)?.takeIf {
                it.contains("://") || it.contains("[Interface]", ignoreCase = true) ||
                    looksLikeOpenVpn(it) || it.contains("#profile-title", ignoreCase = true) ||
                    it.contains("base64:", ignoreCase = true)
            } ?: raw
        }
        if (decodedDocument.contains("[Interface]", ignoreCase = true) || looksLikeOpenVpn(decodedDocument)) return parseBatch(decodedDocument)

        val sourceLines = decodedDocument.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .take(501)
            .toList()
        require(sourceLines.size <= 500) { "حداکثر ۵۰۰ کانفیگ در هر بار قابل ورود است." }

        val lines = sourceLines.flatMap { line ->
            val payload = line.removePrefix("base64:").removePrefix("BASE64:").trim()
            val unwrapped = decodeBase64Text(payload)
            if (unwrapped != null && (unwrapped.trimStart().startsWith("[Interface]", ignoreCase = true) ||
                    looksLikeOpenVpn(unwrapped))) {
                listOf(unwrapped)
            } else if (unwrapped != null && unwrapped.contains("://")) {
                unwrapped.lineSequence().map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            } else listOf(line)
        }.take(501)
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

    private fun decodeBase64Text(value: String): String? = runCatching {
        String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8)
            .takeIf { decoded -> decoded.isNotBlank() && decoded.none { it == '\u0000' } }
    }.getOrNull()

    fun parse(input: String): ProfileParseResult {
        val raw = input.trim()
        if (raw.isEmpty() || raw.length > 64_000) {
            return ProfileParseResult.Failure("لینک خالی یا بیش از حد طولانی است.")
        }
        if (looksLikeOpenVpn(raw)) {
            return parseOpenVpn(raw)
        }
        if (raw.contains("[Interface]", ignoreCase = true)) {
            return parseWireGuard(raw)
        }
        if (raw.startsWith("openvpn-conf://", ignoreCase = true)) {
            return runCatching {
                val config = OpenVpnConfigParser.decodeStored(raw)
                val remote = config.remotes.first()
                ProfileParseResult.Success(ImportedProfile(
                    id = java.util.UUID.randomUUID().toString(),
                    protocol = ProfileProtocol.OPENVPN,
                    displayName = "OpenVPN • ${remote.host}",
                    host = remote.host,
                    port = remote.port,
                    sourceUri = raw
                ))
            }.getOrElse { ProfileParseResult.Failure(it.message ?: "OpenVPN profile invalid.") }
        }
        if (raw.startsWith("wireguard-conf://", ignoreCase = true)) {
            return runCatching {
                val config = WireGuardConfigParser.decodeStored(raw)
                ProfileParseResult.Success(ImportedProfile(
                    id = java.util.UUID.randomUUID().toString(),
                    protocol = if (config.peers.first().publicKey == WARP_PEER_PUBLIC_KEY) ProfileProtocol.WARP else ProfileProtocol.WIREGUARD,
                    displayName = if (config.peers.first().publicKey == WARP_PEER_PUBLIC_KEY) "Cloudflare WARP • ${config.peers.first().host}" else "WireGuard • ${config.peers.first().host}",
                    host = config.peers.first().host,
                    port = config.peers.first().port,
                    sourceUri = raw
                ))
            }.getOrElse { ProfileParseResult.Failure(it.message ?: "WireGuard profile invalid.") }
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



    private fun looksLikeOpenVpn(value: String): Boolean =
        value.contains("<ca>", ignoreCase = true) ||
            (Regex("(?m)^\\s*client\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(value) &&
                Regex("(?m)^\\s*remote\\s+", RegexOption.IGNORE_CASE).containsMatchIn(value))

    private fun parseOpenVpn(raw: String): ProfileParseResult = runCatching {
        val config = OpenVpnConfigParser.parse(raw)
        val remote = config.remotes.first()
        ProfileParseResult.Success(ImportedProfile(
            id = java.util.UUID.randomUUID().toString(),
            protocol = ProfileProtocol.OPENVPN,
            displayName = "OpenVPN • ${remote.host}",
            host = remote.host,
            port = remote.port,
            sourceUri = OpenVpnConfigParser.encodeForStorage(raw)
        ))
    }.getOrElse { ProfileParseResult.Failure(it.message ?: "فایل OpenVPN معتبر نیست یا گزینه‌های آن پشتیبانی نمی‌شوند.") }

    private fun parseWireGuard(raw: String): ProfileParseResult = runCatching {
        val config = WireGuardConfigParser.parse(raw)
        val peer = config.peers.first()
        ProfileParseResult.Success(ImportedProfile(
            id = java.util.UUID.randomUUID().toString(),
            protocol = if (peer.publicKey == WARP_PEER_PUBLIC_KEY) ProfileProtocol.WARP else ProfileProtocol.WIREGUARD,
            displayName = if (peer.publicKey == WARP_PEER_PUBLIC_KEY) "Cloudflare WARP • ${peer.host}" else "WireGuard • ${peer.host}",
            host = peer.host,
            port = peer.port,
            sourceUri = WireGuardConfigParser.encodeForStorage(raw)
        ))
    }.getOrElse { ProfileParseResult.Failure(it.message ?: "فایل WireGuard معتبر نیست.") }

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
