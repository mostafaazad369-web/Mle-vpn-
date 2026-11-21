package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URI

data class WireGuardPeer(
    val host: String,
    val port: Int,
    val publicKey: String,
    val presharedKey: String?,
    val allowedIps: List<String>,
    val keepaliveSeconds: Int?,
    val reserved: List<Int>?
)

data class WireGuardConfig(
    val addresses: List<String>,
    val privateKey: String,
    val dnsServers: List<String>,
    val mtu: Int,
    val peers: List<WireGuardPeer>
) {
    fun toSingBoxEndpoint(tag: String = "wg-ep"): JSONObject {
        val peerJson = JSONArray()
        peers.forEach { peer ->
            val item = JSONObject()
                .put("address", peer.host)
                .put("port", peer.port)
                .put("public_key", peer.publicKey)
                .put("allowed_ips", JSONArray(peer.allowedIps))
            peer.presharedKey?.let { item.put("pre_shared_key", it) }
            peer.keepaliveSeconds?.let { item.put("persistent_keepalive_interval", it) }
            peer.reserved?.let { item.put("reserved", JSONArray(it)) }
            peerJson.put(item)
        }
        return JSONObject()
            .put("type", "wireguard")
            .put("tag", tag)
            .put("system", false)
            .put("mtu", mtu)
            .put("address", JSONArray(addresses))
            .put("private_key", privateKey)
            .put("peers", peerJson)
    }
}

object WireGuardConfigParser {
    private const val PREFIX = "wireguard-conf://"

    fun parse(text: String): WireGuardConfig {
        require(text.length <= 64_000) { "فایل WireGuard بیش از حد بزرگ است." }
        val sections = mutableListOf<Pair<String, MutableMap<String, String>>>()
        var current: Pair<String, MutableMap<String, String>>? = null
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').substringBefore(';').trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                val section = line.substring(1, line.length - 1).trim().lowercase()
                require(section == "interface" || section == "peer") { "بخش ناشناخته در خط ${index + 1}" }
                current = section to linkedMapOf()
                sections += current!!
            } else {
                val target = current ?: error("پارامتر خارج از بخش در خط ${index + 1}")
                val split = line.indexOf('=')
                require(split > 0) { "ساختار خط ${index + 1} معتبر نیست." }
                val key = line.substring(0, split).trim().lowercase()
                val value = line.substring(split + 1).trim()
                require(key !in target.second) { "پارامتر تکراری $key در خط ${index + 1}" }
                target.second[key] = value
            }
        }
        val interfaces = sections.filter { it.first == "interface" }
        val peerSections = sections.filter { it.first == "peer" }
        require(interfaces.size == 1) { "فایل باید دقیقاً یک بخش [Interface] داشته باشد." }
        require(peerSections.isNotEmpty() && peerSections.size <= 16) { "حداقل یک Peer و حداکثر ۱۶ Peer لازم است." }
        val iface = interfaces.single().second
        val allowedInterfaceKeys = setOf("privatekey", "address", "dns", "mtu")
        require(iface.keys.all { it in allowedInterfaceKeys }) {
            "فایل شامل گزینه‌های Interface ناشناخته یا اجراهای اسکریپتی است؛ برای امنیت فقط PrivateKey، Address، DNS و MTU پذیرفته می‌شوند."
        }
        val addresses = csv(iface["address"])
        require(addresses.isNotEmpty() && addresses.all(::isCidr)) { "Address معتبر در [Interface] الزامی است." }
        val privateKey = iface["privatekey"].orEmpty()
        require(isKey(privateKey)) { "PrivateKey باید کلید WireGuard معتبر و Base64 باشد." }
        val dns = csv(iface["dns"])
        require(dns.size <= 8 && dns.all { it.length <= 253 && it.none(Char::isWhitespace) }) { "DNS نامعتبر است." }
        val mtu = iface["mtu"]?.toIntOrNull() ?: 1408
        require(mtu in 576..9000) { "MTU باید بین 576 و 9000 باشد." }
        val peers = peerSections.map { (_, values) ->
            val allowedPeerKeys = setOf("publickey", "presharedkey", "endpoint", "allowedips", "persistentkeepalive", "reserved")
            require(values.keys.all { it in allowedPeerKeys }) { "گزینه Peer ناشناخته است؛ فایل‌های دارای تنظیمات سفارشی/اسکریپتی پذیرفته نمی‌شوند." }
            val endpoint = values["endpoint"] ?: error("Endpoint در Peer وجود ندارد.")
            val (host, port) = parseEndpoint(endpoint)
            val publicKey = values["publickey"].orEmpty()
            require(isKey(publicKey)) { "PublicKey در Peer معتبر نیست." }
            val psk = values["presharedkey"]?.takeIf { it.isNotBlank() }
            require(psk == null || isKey(psk)) { "PresharedKey معتبر نیست." }
            val ips = csv(values["allowedips"])
            require(ips.isNotEmpty() && ips.all(::isCidr)) { "AllowedIPs معتبر در Peer الزامی است." }
            val keepalive = values["persistentkeepalive"]?.toIntOrNull()
            require(keepalive == null || keepalive in 0..65535) { "PersistentKeepalive معتبر نیست." }
            val reserved = values["reserved"]?.let { raw ->
                csv(raw).map { it.toIntOrNull() ?: error("Reserved باید سه عدد باشد.") }
                    .also { require(it.size == 3 && it.all { n -> n in 0..255 }) { "Reserved باید سه عدد بین صفر تا ۲۵۵ باشد." } }
            }
            WireGuardPeer(host, port, publicKey, psk, ips, keepalive?.takeIf { it > 0 }, reserved)
        }
        return WireGuardConfig(addresses, privateKey, dns, mtu, peers)
    }

    fun encodeForStorage(text: String): String =
        PREFIX + Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decodeStored(value: String): WireGuardConfig {
        require(value.startsWith(PREFIX)) { "WireGuard profile encoding is invalid." }
        val raw = Base64.decode(value.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return parse(raw.toString(Charsets.UTF_8))
    }

    private fun parseEndpoint(value: String): Pair<String, Int> {
        val uri = URI("udp://$value")
        val host = uri.host ?: error("Endpoint host معتبر نیست.")
        val port = uri.port
        require(port in 1..65535) { "Endpoint port معتبر نیست." }
        return host to port
    }

    private fun csv(value: String?): List<String> =
        value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    private fun isKey(value: String): Boolean = runCatching {
        val decoded = Base64.decode(value, Base64.DEFAULT)
        decoded.size == 32 && Base64.encodeToString(decoded, Base64.NO_WRAP) == value
    }.getOrDefault(false)

    private fun isCidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2) return false
        val prefix = parts[1].toIntOrNull() ?: return false
        val max = if (parts[0].contains(':')) 128 else 32
        return prefix in 0..max && parts[0].matches(Regex("[0-9a-fA-F:.]+"))
    }
}
