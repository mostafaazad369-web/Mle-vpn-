package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

/**
 * Strict first-pass importer for a single-peer wg-quick configuration.
 *
 * It deliberately rejects hooks, multiple peers, and unknown directives rather
 * than silently dropping behavior. The source text contains a private key and
 * must only be persisted through the encrypted profile vault.
 */
data class WireGuardProfileData(
    val addresses: List<String>,
    val privateKey: String,
    val peerHost: String,
    val peerPort: Int,
    val peerPublicKey: String,
    val presharedKey: String?,
    val allowedIps: List<String>,
    val persistentKeepalive: Int,
    val listenPort: Int?,
    val mtu: Int?
)

object WireGuardConfigParser {
    private val allowedInterfaceKeys = setOf("address", "privatekey", "listenport", "mtu")
    private val allowedPeerKeys = setOf(
        "publickey", "presharedkey", "endpoint", "allowedips", "persistentkeepalive"
    )

    fun parse(text: String): WireGuardProfileData {
        require(text.length in 1..64_000) { "فایل WireGuard خالی یا بیش از حد بزرگ است." }
        var section = ""
        val interfaceValues = linkedMapOf<String, String>()
        val peerValues = linkedMapOf<String, String>()
        text.lineSequence().forEachIndexed { index, original ->
            val line = original.substringBefore('#').substringBefore(';').trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                require(section == "interface" || section == "peer") {
                    "بخش ناشناخته در خط ${index + 1}."
                }
                return@forEachIndexed
            }
            require(section.isNotEmpty()) { "دستور خارج از بخش در خط ${index + 1}." }
            val split = line.indexOf('=')
            require(split > 0) { "ساختار خط ${index + 1} معتبر نیست." }
            val key = line.substring(0, split).trim().lowercase()
            val value = line.substring(split + 1).trim()
            require(value.isNotEmpty()) { "مقدار خالی در خط ${index + 1}." }
            val target = if (section == "interface") interfaceValues else peerValues
            val allowed = if (section == "interface") allowedInterfaceKeys else allowedPeerKeys
            require(key in allowed) { "گزینهٔ $key در این نسخه پشتیبانی نمی‌شود." }
            require(target.putIfAbsent(key, value) == null) { "گزینهٔ تکراری: $key" }
        }
        require(interfaceValues.isNotEmpty() && peerValues.isNotEmpty()) {
            "بخش‌های Interface و Peer هر دو الزامی هستند."
        }
        val addresses = csv(interfaceValues["address"] ?: error("Address الزامی است."))
        require(addresses.isNotEmpty() && addresses.all(::isCidr)) { "Address معتبر نیست." }
        val privateKey = interfaceValues["privatekey"] ?: error("PrivateKey الزامی است.")
        require(isWireGuardKey(privateKey)) { "PrivateKey باید کلید WireGuard معتبر ۳۲ بایتی باشد." }
        val publicKey = peerValues["publickey"] ?: error("PublicKey الزامی است.")
        require(isWireGuardKey(publicKey)) { "PublicKey باید کلید WireGuard معتبر ۳۲ بایتی باشد." }
        val psk = peerValues["presharedkey"]
        require(psk == null || isWireGuardKey(psk)) { "PresharedKey معتبر نیست." }
        val endpoint = parseEndpoint(peerValues["endpoint"] ?: error("Endpoint الزامی است."))
        val allowedIps = csv(peerValues["allowedips"] ?: error("AllowedIPs الزامی است."))
        require(allowedIps.isNotEmpty() && allowedIps.all(::isCidr)) { "AllowedIPs معتبر نیست." }
        val keepalive = peerValues["persistentkeepalive"]?.toIntOrNull() ?: 0
        require(keepalive in 0..65535) { "PersistentKeepalive معتبر نیست." }
        val mtu = interfaceValues["mtu"]?.toIntOrNull()
        require(mtu == null || mtu in 576..9000) { "MTU خارج از محدودهٔ مجاز است." }
        require(interfaceValues["listenport"] == null ||
            (interfaceValues["listenport"]!!.toIntOrNull()?.let { it in 1..65535 } == true)) { "ListenPort معتبر نیست." }
        return WireGuardProfileData(
            addresses, privateKey, endpoint.first, endpoint.second, publicKey, psk,
            allowedIps, keepalive, interfaceValues["listenport"]?.toInt(), mtu
        )
    }

    private fun csv(value: String) = value.split(',').map(String::trim).filter(String::isNotEmpty)

    private fun parseEndpoint(value: String): Pair<String, Int> {
        val host: String
        val portText: String
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            require(close > 1 && value.getOrNull(close + 1) == ':') { "Endpoint IPv6 معتبر نیست." }
            host = value.substring(1, close)
            portText = value.substring(close + 2)
            require(host.contains(':')) { "آدرس IPv6 معتبر نیست." }
            InetAddress.getByName(host)
        } else {
            val split = value.lastIndexOf(':')
            require(split > 0 && value.indexOf(':') == split) {
                "برای IPv6 در Endpoint از کروشه استفاده کنید."
            }
            host = value.substring(0, split)
            portText = value.substring(split + 1)
            require(host.matches(Regex("(?i)[a-z0-9.-]+")) && !host.startsWith(".") && !host.endsWith(".")) {
                "نام میزبان Endpoint معتبر نیست."
            }
        }
        val port = portText.toIntOrNull()
        require(port != null && port in 1..65535) { "پورت Endpoint معتبر نیست." }
        return host to port
    }

    private fun isWireGuardKey(value: String): Boolean = runCatching {
        Base64.decode(value, Base64.DEFAULT).size == 32
    }.getOrDefault(false)

    private fun isCidr(value: String): Boolean = runCatching {
        val split = value.lastIndexOf('/')
        if (split <= 0) return@runCatching false
        val address = value.substring(0, split)
        val prefix = value.substring(split + 1).toIntOrNull() ?: return@runCatching false
        if (address.contains(':')) {
            require(prefix in 0..128)
            InetAddress.getByName(address).address.size == 16
        } else {
            require(prefix in 0..32)
            val parts = address.split('.')
            parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        }
    }.getOrDefault(false)
}

/** Builds the sing-box >=1.11 WireGuard endpoint schema, not the removed legacy outbound. */
object WireGuardEndpointBuilder {
    fun build(profile: ImportedProfile): JSONObject {
        require(profile.protocol == ProfileProtocol.WIREGUARD) { "پروفایل WireGuard لازم است." }
        val data = WireGuardConfigParser.parse(profile.sourceUri)
        val peer = JSONObject()
            .put("address", data.peerHost)
            .put("port", data.peerPort)
            .put("public_key", data.peerPublicKey)
            .put("allowed_ips", JSONArray(data.allowedIps))
            .put("persistent_keepalive_interval", data.persistentKeepalive)
        data.presharedKey?.let { peer.put("pre_shared_key", it) }
        return JSONObject()
            .put("type", "wireguard")
            .put("tag", "wireguard-endpoint")
            .put("mtu", data.mtu ?: 1408)
            .put("address", JSONArray(data.addresses))
            .put("private_key", data.privateKey)
            .put("peers", JSONArray().put(peer))
            .also { endpoint -> data.listenPort?.let { endpoint.put("listen_port", it) } }
    }
}
