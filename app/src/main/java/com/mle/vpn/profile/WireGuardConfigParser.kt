package com.mle.vpn.profile

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

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
    fun toConf(): String = buildString {
        append("[Interface]\n")
        append("PrivateKey = ").append(privateKey).append('\n')
        append("Address = ").append(addresses.joinToString(", ")).append('\n')
        if (dnsServers.isNotEmpty()) append("DNS = ").append(dnsServers.joinToString(", ")).append('\n')
        append("MTU = ").append(mtu).append('\n')
        peers.forEach { peer ->
            append("\n[Peer]\n")
            append("PublicKey = ").append(peer.publicKey).append('\n')
            peer.presharedKey?.let { append("PresharedKey = ").append(it).append('\n') }
            append("Endpoint = ").append(if (peer.host.contains(':')) "[${peer.host}]:${peer.port}" else "${peer.host}:${peer.port}").append('\n')
            append("AllowedIPs = ").append(peer.allowedIps.joinToString(", ")).append('\n')
            peer.keepaliveSeconds?.let { append("PersistentKeepalive = ").append(it).append('\n') }
            peer.reserved?.let { append("Reserved = ").append(it.joinToString(", ")).append('\n') }
        }
    }

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
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
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
        val amneziaOptions = setOf("jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5")
        require(iface.keys.none { it in amneziaOptions }) {
            "این فایل AmneziaWG است. برای جلوگیری از حذف تنظیمات مبهم‌سازی، تا زمان نصب موتور اختصاصی AmneziaWG وارد نمی‌شود."
        }
        val allowedInterfaceKeys = setOf("privatekey", "address", "dns", "mtu")
        require(iface.keys.all { it in allowedInterfaceKeys }) {
            "فایل شامل گزینه‌های Interface ناشناخته یا اجراهای اسکریپتی است؛ برای امنیت فقط PrivateKey، Address، DNS و MTU پذیرفته می‌شوند."
        }
        val addresses = csv(iface["address"])
        require(addresses.isNotEmpty() && addresses.all(::isCidr)) { "Address معتبر در [Interface] الزامی است." }
        val privateKey = iface["privatekey"].orEmpty()
        require(isKey(privateKey)) { "PrivateKey باید کلید WireGuard معتبر و Base64 باشد." }
        val dns = csv(iface["dns"])
        require(dns.size <= 8 && dns.all(::isDnsServer)) { "DNS نامعتبر است." }
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

    fun parseUri(link: String): Pair<WireGuardConfig, String> {
        val raw = link.trim()
        val scheme = raw.substringBefore("://", "").lowercase()
        require(scheme == "wireguard" || scheme == "wg") { "لینک WireGuard معتبر نیست." }
        val rest = raw.substringAfter("://")
        val hash = rest.indexOf('#')
        val name = if (hash >= 0) decodeUri(rest.substring(hash + 1)) else ""
        val noFragment = if (hash >= 0) rest.substring(0, hash) else rest
        val queryIndex = noFragment.indexOf('?')
        val authority = (if (queryIndex >= 0) noFragment.substring(0, queryIndex) else noFragment).trimEnd('/')
        val at = authority.lastIndexOf('@')
        require(at > 0) { "لینک WireGuard باید private-key@host:port داشته باشد." }
        val privateKey = decodeUri(authority.substring(0, at)).trim()
        val (host, port) = parseEndpoint(authority.substring(at + 1))
        val query = linkedMapOf<String, String>()
        if (queryIndex >= 0) {
            noFragment.substring(queryIndex + 1).split('&').filter(String::isNotBlank).forEach { pair ->
                val split = pair.indexOf('=')
                require(split > 0) { "پارامتر لینک WireGuard معتبر نیست." }
                val key = decodeUri(pair.substring(0, split)).lowercase()
                val value = decodeUri(pair.substring(split + 1))
                require(query.putIfAbsent(key, value) == null) { "پارامتر تکراری در لینک WireGuard: $key" }
            }
        }
        val allowedKeys = setOf(
            "publickey", "public_key", "peer_public_key", "pk", "address", "ip",
            "allowedips", "allowed_ips", "dns", "mtu", "keepalive", "persistentkeepalive",
            "presharedkey", "psk", "reserved"
        )
        require(query.keys.all { it in allowedKeys }) {
            "لینک شامل گزینه‌های اختصاصی AmneziaWG یا پارامتر پشتیبانی‌نشده است؛ از تبدیل بی‌صدای آن به WireGuard جلوگیری شد."
        }
        require(isKey(privateKey)) { "کلید خصوصی WireGuard معتبر نیست." }
        val publicKey = (query["publickey"] ?: query["public_key"] ?: query["peer_public_key"] ?: query["pk"]).orEmpty()
        require(isKey(publicKey)) { "کلید عمومی Peer معتبر نیست." }
        val addresses = csv(query["address"] ?: query["ip"])
        require(addresses.isNotEmpty() && addresses.all(::isCidr)) { "آدرس محلی WireGuard باید با CIDR مشخص شود." }
        val dns = csv(query["dns"])
        require(dns.size <= 8 && dns.all(::isDnsServer)) { "DNS در لینک WireGuard معتبر نیست." }
        val mtuRaw = query["mtu"]
        val mtu = mtuRaw?.toIntOrNull() ?: if (mtuRaw.isNullOrBlank()) 1408 else error("MTU در لینک WireGuard معتبر نیست.")
        require(mtu in 576..9000) { "MTU در لینک WireGuard خارج از محدوده است." }
        val allowedIps = csv(query["allowedips"] ?: query["allowed_ips"]).ifEmpty { listOf("0.0.0.0/0", "::/0") }
        require(allowedIps.all(::isCidr)) { "AllowedIPs در لینک WireGuard معتبر نیست." }
        val keepaliveRaw = query["keepalive"] ?: query["persistentkeepalive"]
        val keepalive = keepaliveRaw?.toIntOrNull()
        require(keepaliveRaw.isNullOrBlank() || keepalive != null && keepalive in 0..65535) { "Keepalive در لینک WireGuard معتبر نیست." }
        val psk = (query["presharedkey"] ?: query["psk"])?.takeIf(String::isNotBlank)
        require(psk == null || isKey(psk)) { "PresharedKey در لینک WireGuard معتبر نیست." }
        val reservedRaw = query["reserved"]
        val reserved = if (reservedRaw.isNullOrBlank()) null else csv(reservedRaw).map {
            it.toIntOrNull() ?: error("Reserved باید سه عدد باشد.")
        }.also { require(it.size == 3 && it.all { value -> value in 0..255 }) { "Reserved باید سه عدد بین صفر تا ۲۵۵ باشد." } }
        val peer = WireGuardPeer(host, port, publicKey, psk, allowedIps, keepalive?.takeIf { it > 0 }, reserved)
        return WireGuardConfig(addresses, privateKey, dns, mtu, listOf(peer)) to name
    }

    private fun decodeUri(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

    fun encodeForStorage(text: String): String =
        PREFIX + Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decodeStored(value: String): WireGuardConfig {
        require(value.startsWith(PREFIX)) { "WireGuard profile encoding is invalid." }
        val raw = Base64.decode(value.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return parse(raw.toString(Charsets.UTF_8))
    }

    private fun parseEndpoint(value: String): Pair<String, Int> {
        val uri = URI("udp://$value")
        val host = uri.host?.removePrefix("[")?.removeSuffix("]") ?: error("Endpoint host معتبر نیست.")
        require(uri.rawUserInfo == null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null) {
            "Endpoint فقط باید شامل host و port باشد."
        }
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

    private fun isDnsServer(value: String): Boolean {
        if (value.length !in 1..253 || value.any(Char::isWhitespace)) return false
        if (value.contains(':') || value.matches(Regex("[0-9.]+"))) {
            return runCatching {
                val address = java.net.InetAddress.getByName(value).address
                address.size == 4 || address.size == 16
            }.getOrDefault(false)
        }
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun isCidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2) return false
        val prefix = parts[1].toIntOrNull() ?: return false
        val address = parts[0]
        return if (address.contains(':')) {
            prefix in 0..128 && runCatching {
                java.net.InetAddress.getByName(address).address.size == 16
            }.getOrDefault(false)
        } else {
            val octets = address.split('.')
            prefix in 0..32 && octets.size == 4 &&
                octets.all { it.toIntOrNull()?.let { number -> number in 0..255 } == true }
        }
    }
}
