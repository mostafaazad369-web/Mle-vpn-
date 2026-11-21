package com.mle.vpn.engine.catalog

/**
 * Inventory of MLM VPN connection methods being evaluated for MLE VPN.
 *
 * The iconKey is intentionally stable and unique per tile so the Compose UI can map it to
 * dedicated vector artwork without coupling engine logic to presentation. Catalog presence
 * does not mean that a backend is installed or that a connection can be started.
 */
data class MleEngineCatalogItem(
    val id: String,
    val title: String,
    val iconKey: String,
    val integration: Integration,
    val detail: String
)

enum class Integration {
    /** Supported by the existing MLE sing-box converter/runtime, subject to profile compatibility. */
    EXISTING_SING_BOX_PATH,
    /** UI/catalog entry only; a real engine adapter and lifecycle are still required. */
    ADAPTER_REQUIRED,
    /** Requires a separately reviewed native core, SDK, or provider integration. */
    NATIVE_OR_PROVIDER_REQUIRED,
    /** Requires user-supplied configuration, account, or credentials. */
    USER_CONFIG_OR_ACCOUNT_REQUIRED,
    /** Diagnostic/network utility only; it is not a VPN transport. */
    DIAGNOSTIC_ONLY
}

object MleEngineCatalog {
    val items: List<MleEngineCatalogItem> = listOf(
        MleEngineCatalogItem("vless", "VLESS", "vless", Integration.EXISTING_SING_BOX_PATH, "Only variants accepted by the current outbound builder."),
        MleEngineCatalogItem("vmess", "VMess", "vmess", Integration.EXISTING_SING_BOX_PATH, "Only variants accepted by the current outbound builder."),
        MleEngineCatalogItem("trojan", "Trojan", "trojan", Integration.EXISTING_SING_BOX_PATH, "Only variants accepted by the current outbound builder."),
        MleEngineCatalogItem("shadowsocks", "Shadowsocks", "shadowsocks", Integration.EXISTING_SING_BOX_PATH, "SIP002 and legacy import plus sing-box outbound conversion are implemented; plugin transports fail closed."),
        MleEngineCatalogItem("wireguard", "WireGuard", "wireguard", Integration.EXISTING_SING_BOX_PATH, "Strict single-peer wg-quick import, encrypted vault storage and sing-box endpoint config assembly are implemented; real-device routing verification remains."),
        MleEngineCatalogItem("warp", "Cloudflare WARP", "warp", Integration.ADAPTER_REQUIRED, "Provider-compatible WARP configuration and real exit verification required."),
        MleEngineCatalogItem("masque", "MASQUE", "masque", Integration.ADAPTER_REQUIRED, "Separate MASQUE transport and runtime adapter required."),
        MleEngineCatalogItem("warp-on-warp", "WARP-in-WARP", "warp_on_warp", Integration.ADAPTER_REQUIRED, "Nested tunnel orchestration and routing tests required."),
        MleEngineCatalogItem("amneziawg", "AmneziaWG", "amneziawg", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Review upstream core, ABI coverage and license before vendoring."),
        MleEngineCatalogItem("hysteria2", "Hysteria2", "hysteria2", Integration.EXISTING_SING_BOX_PATH, "Outbound conversion is implemented; UDP behavior and real-device interoperability still require testing."),
        MleEngineCatalogItem("tuic", "TUIC", "tuic", Integration.EXISTING_SING_BOX_PATH, "Outbound conversion is implemented with UUID/password, TLS and supported congestion/UDP options; device verification remains."),
        MleEngineCatalogItem("openvpn", "OpenVPN", "openvpn", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "OpenVPN 3 native core/JNI, shared VPN service lifecycle and .ovpn preflight are integrated; both Android ABI libraries are packaged and CI-verified, while real-device connection and leak checks remain."),
        MleEngineCatalogItem("npv", "NPV", "npv", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "MLM's NPV module is a profile library: standard VLESS/VMess/Trojan/Shadowsocks/Hysteria2/TUIC links run through Xray, while native .npvt/.npvs files are only handed to the separate NPV Tunnel app. MLE's shared importer already accepts supported standard links; native NPV file execution is not implemented and must not be implied."),
        MleEngineCatalogItem("outline", "Outline", "outline", Integration.EXISTING_SING_BOX_PATH, "Outline access keys use the existing Shadowsocks SIP002 parser and sing-box outbound path; unsupported plugin transports remain rejected."),
        MleEngineCatalogItem("psiphon", "Psiphon", "psiphon", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Review official SDK availability, terms and Android lifecycle."),
        MleEngineCatalogItem("tor", "Tor", "tor", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Tor runtime, bridge management and safety testing required."),
        MleEngineCatalogItem("geph", "Geph", "geph", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Review upstream SDK and account requirements."),
        MleEngineCatalogItem("lantern", "Lantern", "lantern", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Review upstream SDK and distribution terms."),
        MleEngineCatalogItem("urnetwork", "URnetwork", "urnetwork", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Review SDK availability, provider terms and network lifecycle."),
        MleEngineCatalogItem("proton", "Proton VPN", "proton", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "User account and supported provider protocol required."),
        MleEngineCatalogItem("windscribe", "Windscribe", "windscribe", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "User account and supported provider protocol required."),
        MleEngineCatalogItem("fenix", "FENIX / Firefox VPN", "fenix", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "Verify provider availability, account flow and current service terms."),
        MleEngineCatalogItem("vpngate", "VPN Gate", "vpngate", Integration.ADAPTER_REQUIRED, "Public server discovery plus supported protocol adapter required."),
        MleEngineCatalogItem("quick-connect", "Quick Connect", "quick", Integration.ADAPTER_REQUIRED, "Build a real probe/ranking pipeline; never label a TCP probe as VPN success."),
        MleEngineCatalogItem("flux", "FLUX", "flux", Integration.ADAPTER_REQUIRED, "Adaptive route selection and independent exit verification required."),
        MleEngineCatalogItem("mae", "MAE", "mae", Integration.ADAPTER_REQUIRED, "Per-app/per-network policy engine; requires a tested routing architecture."),
        MleEngineCatalogItem("github-tunnel", "GitHub Tunnel", "github", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "User-owned infrastructure and explicit credentials required."),
        MleEngineCatalogItem("telegram-proxy", "Telegram Proxy", "telegram-proxy", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "User-owned Cloudflare deployment and proxy configuration required."),
        MleEngineCatalogItem("iran-configs", "Iran Configs", "iran", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "Import user-provided profiles; do not bundle unverified endpoints."),
        MleEngineCatalogItem("sni-fronting", "SNI / ECH", "fronting", Integration.ADAPTER_REQUIRED, "Implement only supported standards and test platform compatibility."),
        MleEngineCatalogItem("cloudflare-panel", "Cloudflare Panel", "cloudflare", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "User-owned Cloudflare account and Worker deployment required."),
        MleEngineCatalogItem("cloudflare-doctor", "Cloudflare Doctor", "cfdoctor", Integration.DIAGNOSTIC_ONLY, "Cloudflare trace diagnostics are available; this checks the current route and WARP flag but does not create a VPN tunnel."),
        MleEngineCatalogItem("config-studio", "Config Studio", "configstudio", Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED, "User-owned Worker backend; never embed private deployment credentials."),
        MleEngineCatalogItem("mlm-scout", "MLM Scout", "scout", Integration.ADAPTER_REQUIRED, "Cancellable real-route tests and transparent ranking required."),
        MleEngineCatalogItem("game-booster", "Game Booster", "game", Integration.ADAPTER_REQUIRED, "Latency measurement and per-game routing require device validation."),
        MleEngineCatalogItem("gereh", "Gereh Messenger", "gereh", Integration.NATIVE_OR_PROVIDER_REQUIRED, "Separate audit of Rust crypto, Android bridge, Olm/Megolm dependencies and relay protocol is required before integration.")
    )

    private val ids = items.map { it.id }.toSet()
    init {
        require(ids.size == items.size) { "Engine catalog IDs must be unique" }
        require(items.map { it.iconKey }.toSet().size == items.size) {
            "Every engine must have its own unique iconKey"
        }
    }

    fun find(id: String): MleEngineCatalogItem? = items.firstOrNull { it.id == id }
}
