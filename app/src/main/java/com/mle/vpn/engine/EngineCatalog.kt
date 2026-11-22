package com.mle.vpn.engine

/**
 * Product-level registry of connection engines planned for MLE VPN.
 * Catalog entries are not executable VPN implementations until a native
 * adapter is integrated, licensed, packaged and tested on real devices.
 */
enum class IntegrationState { CATALOG_ONLY, ADAPTER_IN_PROGRESS, DEVICE_VERIFIED }

data class EngineDescriptor(
    val id: String,
    val name: String,
    val source: String,
    val protocols: List<String>,
    val requirement: String,
    val state: IntegrationState = IntegrationState.CATALOG_ONLY
)

object EngineCatalog {
    val all: List<EngineDescriptor> = listOf(
        EngineDescriptor("sing_box", "NekoBox / sing-box", "NekoBox / SagerNet",
            listOf("VLESS", "VMess", "Trojan", "Shadowsocks", "ShadowsocksR", "Hysteria 2", "TUIC", "AnyTLS", "ShadowTLS", "NaiveProxy", "WireGuard", "AmneziaWG", "SSH", "SOCKS", "HTTP", "Tailscale", "Nebula"),
            "Requires a valid node or subscription."),
        EngineDescriptor("slipnet", "SlipNet", "anonvector/SlipNet",
            listOf("DNSTT", "NoizDNS", "VayDNS", "Slipstream", "SSH", "NaiveProxy", "DNS-over-HTTPS", "Tor bridges", "SSH chaining"),
            "Requires a compatible server and profile."),
        EngineDescriptor("xray", "Xray Core", "Xray-project",
            listOf("VLESS", "VMess", "Trojan", "Shadowsocks", "REALITY", "TLS", "WebSocket", "gRPC", "XHTTP"),
            "Requires a valid node or subscription."),
        EngineDescriptor("warp", "Cloudflare WARP", "Cloudflare",
            listOf("WARP", "WARP over WireGuard"), "Requires client registration and WARP configuration."),
        EngineDescriptor("aether", "Aether", "MLM VPN / Aether",
            listOf("MASQUE", "WireGuard", "WARP-in-WARP"), "Requires native runtime and endpoint configuration."),
        EngineDescriptor("amnezia", "Amnezia", "Amnezia",
            listOf("AmneziaWG", "WireGuard", "Hysteria 2"), "Requires a server or usable configuration."),
        EngineDescriptor("openvpn", "OpenVPN", "OpenVPN",
            listOf("OpenVPN UDP", "OpenVPN TCP"), "Requires an .ovpn file or provider account."),
        EngineDescriptor("outline", "Outline", "Jigsaw",
            listOf("Shadowsocks / Outline access key"), "Requires a valid access key."),
        EngineDescriptor("psiphon", "Psiphon", "Psiphon Inc.",
            listOf("Psiphon tunnel"), "Requires permitted official client integration."),
        EngineDescriptor("tor", "Tor", "Tor Project",
            listOf("Tor", "obfs4", "Snowflake", "Meek"), "Requires Tor runtime and sometimes bridges."),
        EngineDescriptor("geph", "Geph", "Geph",
            listOf("Geph transport"), "Requires permitted client integration and service access."),
        EngineDescriptor("lantern", "Lantern", "Lantern",
            listOf("Lantern transport"), "Requires permitted client/SDK and service terms review."),
        EngineDescriptor("proton", "Proton VPN", "Proton VPN",
            listOf("WireGuard", "OpenVPN"), "Requires account and official server access."),
        EngineDescriptor("windscribe", "Windscribe", "Windscribe",
            listOf("WireGuard", "OpenVPN", "IKEv2", "Stealth", "WStunnel"), "Requires account and official configuration."),
        EngineDescriptor("fenix", "FENIX / Firefox VPN", "Mozilla",
            listOf("Provider-managed VPN"), "Requires authentication and service permission."),
        EngineDescriptor("urnetwork", "URnetwork", "URnetwork",
            listOf("URnetwork transport"), "Requires permitted client/SDK and service access."),
        EngineDescriptor("vpngate", "VPN Gate", "VPN Gate",
            listOf("SoftEther", "OpenVPN"), "Requires discovery and validation of public relays."),
        EngineDescriptor("npv", "NPV Configs", "NPV-compatible providers",
            listOf("NPV configuration formats"), "Requires compatible parser and valid profiles."),
        EngineDescriptor("sni_ech", "SNI / ECH tools", "Independent transport module",
            listOf("ECH", "DNS-over-HTTPS"), "Supporting network tools; not a full VPN by themselves.")
    )

    fun find(id: String): EngineDescriptor? = all.firstOrNull { it.id == id }
}
