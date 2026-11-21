package com.mle.vpn.engine

/**
 * Inventory of MLM VPN engine families requested for MLE.
 *
 * IMPORTANT: a catalog entry is not an implementation. Only SING_BOX is currently
 * wired to MLE's VPN service. Every other entry remains unavailable until its
 * compatible runtime, license, configuration flow, lifecycle, and device tests
 * are integrated. The UI must not present CATALOG_ONLY entries as connectable.
 */
enum class IntegrationState { CATALOG_ONLY, ADAPTER_IN_PROGRESS, IMPLEMENTED_UNVERIFIED, DEVICE_VERIFIED }

data class EngineDescriptor(
    val id: String,
    val name: String,
    val source: String,
    val protocols: List<String>,
    val requirement: String,
    val iconKey: String = id,
    val state: IntegrationState = IntegrationState.CATALOG_ONLY
)

object EngineCatalog {
    val all: List<EngineDescriptor> = listOf(
        EngineDescriptor("sing_box", "V2Ray / Xray via sing-box", "SagerNet / sing-box",
            listOf("VLESS", "VMess", "Trojan", "Shadowsocks", "REALITY", "TLS", "WebSocket", "gRPC", "XHTTP", "Hysteria 2", "TUIC", "AnyTLS", "WireGuard"),
            "A compatible node or subscription is required.", "v2ray",
            IntegrationState.IMPLEMENTED_UNVERIFIED),
        EngineDescriptor("warp", "Cloudflare WARP", "Cloudflare",
            listOf("WARP", "WireGuard"), "Requires official WARP registration and runtime.", "warp"),
        EngineDescriptor("aether", "Aether / MASQUE", "MLM VPN / Aether",
            listOf("MASQUE", "WARP-in-WARP", "WireGuard"), "Requires Aether native runtime and endpoints.", "aether"),
        EngineDescriptor("amnezia", "Amnezia", "Amnezia",
            listOf("AmneziaWG", "WireGuard", "Hysteria 2"), "Requires compatible native engine and server profiles.", "amnezia"),
        EngineDescriptor("openvpn", "OpenVPN", "OpenVPN",
            listOf("OpenVPN UDP", "OpenVPN TCP", ".ovpn"), "Requires OpenVPN core and imported/provider profiles.", "openvpn"),
        EngineDescriptor("outline", "Outline", "Jigsaw",
            listOf("Shadowsocks access keys"), "Requires Outline-compatible runtime and access keys.", "outline"),
        EngineDescriptor("psiphon", "Psiphon", "Psiphon Inc.",
            listOf("Psiphon transport"), "Requires permitted official SDK/runtime integration.", "psiphon"),
        EngineDescriptor("tor", "Tor", "Tor Project",
            listOf("Tor", "obfs4", "Snowflake", "Meek"), "Requires Tor runtime and optional bridges.", "tor"),
        EngineDescriptor("geph", "Geph", "Geph",
            listOf("Geph transport"), "Requires permitted client/runtime integration.", "geph"),
        EngineDescriptor("lantern", "Lantern", "Lantern",
            listOf("Lantern transport"), "Requires permitted official client/runtime integration.", "lantern"),
        EngineDescriptor("urnetwork", "URnetwork", "URnetwork",
            listOf("URnetwork multi-provider transport"), "Requires official SDK and account setup.", "urnetwork"),
        EngineDescriptor("proton", "Proton VPN", "Proton VPN",
            listOf("WireGuard", "OpenVPN", "Stealth"), "Requires user account and supported provider integration.", "proton"),
        EngineDescriptor("windscribe", "Windscribe", "Windscribe",
            listOf("WireGuard", "OpenVPN", "IKEv2", "Stealth", "WStunnel"), "Requires user account and supported provider integration.", "windscribe"),
        EngineDescriptor("fenix", "FENIX / Firefox VPN", "Mozilla",
            listOf("Provider-managed VPN"), "Requires eligible Firefox account and permitted integration.", "fenix"),
        EngineDescriptor("vpngate", "VPN Gate", "VPN Gate",
            listOf("SoftEther", "OpenVPN"), "Requires relay discovery and a compatible client runtime.", "vpngate"),
        EngineDescriptor("npv", "NPV Configs", "NPV-compatible providers",
            listOf("NPV", "NPVT", "NPVS"), "Requires compatible parsers; encrypted vendor formats may not be portable.", "npv"),
        EngineDescriptor("flux", "FLUX adaptive engine", "MLM VPN",
            listOf("Adaptive multi-route selection"), "Requires FLUX runtime, route racing, and real exit verification.", "flux"),
        EngineDescriptor("mae", "MLM Adaptive Engine (MAE)", "MLM VPN",
            listOf("Per-app and per-domain adaptive routing"), "Requires its policy engine and user-owned Cloudflare infrastructure.", "mae"),
        EngineDescriptor("gst_edg", "GST / EDG transports", "MLM VPN",
            listOf("Specialized censorship-resistant transports"), "Requires the corresponding upstream native components and server support.", "gst"),
        EngineDescriptor("github_tunnel", "GitHub Tunnel", "MLM VPN",
            listOf("User-deployed cloud tunnel"), "Requires user authorization and a separately deployed backend.", "github"),
        EngineDescriptor("telegram_proxy", "Telegram Proxy", "MLM VPN",
            listOf("MTProto proxy"), "Telegram-only proxy; not a full-device VPN without a separate tunnel.", "telegram"),
        EngineDescriptor("sni_ech", "SNI / ECH tools", "Independent transport tooling",
            listOf("ECH", "DNS-over-HTTPS"), "Supporting network tools, not standalone VPN engines.", "shield")
    )

    fun find(id: String): EngineDescriptor? = all.firstOrNull { it.id == id }
}
