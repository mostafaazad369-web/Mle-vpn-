package com.mle.vpn.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MleConnectionPhase {
    DISCONNECTED,
    STARTING,
    TUNNEL_ACTIVE,
    INTERNET_VERIFIED,
    NO_INTERNET,
    ERROR
}

data class MleConnectionStatus(
    val phase: MleConnectionPhase = MleConnectionPhase.DISCONNECTED,
    val detail: String = "اتصال برقرار نیست"
)

/**
 * In-process connection state shared by the VPN service and Compose UI.
 * TUNNEL_ACTIVE means the libbox service started and Android TUN was opened;
 * it does not claim that the remote server or public internet is reachable.
 */
object MleVpnStatus {
    private val mutableStatus = MutableStateFlow(MleConnectionStatus())
    val status: StateFlow<MleConnectionStatus> = mutableStatus.asStateFlow()

    fun starting() {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.STARTING,
            "در حال راه‌اندازی موتور و ایجاد تونل امن…"
        )
    }

    fun tunnelActive() {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.TUNNEL_ACTIVE,
            "تونل VPN فعال است؛ دسترسی به سرور و اینترنت هنوز جداگانه تأیید نشده است."
        )
    }

    fun internetVerified() {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.INTERNET_VERIFIED,
            "عبور ترافیک و دسترسی HTTPS از داخل VPN تأیید شد."
        )
    }

    fun trafficUnavailable(message: String) {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.NO_INTERNET,
            message.ifBlank { "تونل فعال است اما دسترسی اینترنت از داخل VPN تأیید نشد." }
        )
    }

    fun disconnected() {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.DISCONNECTED,
            "اتصال قطع است"
        )
    }

    fun failed(message: String) {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.ERROR,
            message.ifBlank { "راه‌اندازی VPN ناموفق بود" }
        )
    }
}

/**
 * Runtime capability registry. Only engines with an integrated adapter may be
 * marked AVAILABLE; planned integrations must not be shown as working options.
 */
enum class MleEngineAvailability { AVAILABLE, PLANNED }

data class MleEngineCapability(
    val id: String,
    val title: String,
    val availability: MleEngineAvailability,
    val protocols: Set<String>
)

object MleEngineCatalog {
    val all = listOf(
        MleEngineCapability(
            "sing-box", "sing-box", MleEngineAvailability.AVAILABLE,
            setOf("VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria2", "TUIC", "AnyTLS", "SOCKS", "HTTP")
        ),
        MleEngineCapability("xray", "Xray", MleEngineAvailability.PLANNED, setOf("VLESS", "VMess", "Trojan")),
        MleEngineCapability("wireguard", "WireGuard / WARP", MleEngineAvailability.PLANNED, setOf("WireGuard")),
        MleEngineCapability("amneziawg", "AmneziaWG", MleEngineAvailability.PLANNED, setOf("AmneziaWG")),
        MleEngineCapability("openvpn", "OpenVPN", MleEngineAvailability.PLANNED, setOf("OpenVPN")),
        MleEngineCapability("psiphon", "Psiphon", MleEngineAvailability.PLANNED, setOf("Psiphon")),
        MleEngineCapability("tor", "Tor", MleEngineAvailability.PLANNED, setOf("Tor")),
        MleEngineCapability("geph", "Geph", MleEngineAvailability.PLANNED, setOf("Geph"))
    )

    fun available() = all.filter { it.availability == MleEngineAvailability.AVAILABLE }
    fun planned() = all.filter { it.availability == MleEngineAvailability.PLANNED }
}

/** A route measurement collected by a real probe, not a TCP-only preflight. */
data class MleRouteCandidate(
    val id: String,
    val engineId: String,
    val latencyMs: Long?,
    val successRate: Double,
    val throughputKbps: Long?,
    val consecutiveFailures: Int = 0,
    val supportsUdp: Boolean = true,
    val supportsIpv6: Boolean = true,
    val metered: Boolean = false,
    val endToEndVerified: Boolean = false
)

data class MleRouteRequirements(
    val requireUdp: Boolean = false,
    val requireIpv6: Boolean = false,
    val allowUnverified: Boolean = false,
    val avoidMetered: Boolean = false
)

data class MleScoredRoute(val candidate: MleRouteCandidate, val score: Double)

/**
 * Deterministic FLUX-style route ranking. It does not probe or start engines;
 * it only ranks candidates after real measurements have been supplied.
 */
object MleAdaptiveRoutePlanner {
    fun rank(
        candidates: List<MleRouteCandidate>,
        requirements: MleRouteRequirements = MleRouteRequirements()
    ): List<MleScoredRoute> = candidates.asSequence()
        .filter { it.id.isNotBlank() && it.engineId.isNotBlank() }
        .filter { !requirements.requireUdp || it.supportsUdp }
        .filter { !requirements.requireIpv6 || it.supportsIpv6 }
        .filter { requirements.allowUnverified || it.endToEndVerified }
        .filter { it.consecutiveFailures < 3 }
        .filter { !requirements.avoidMetered || !it.metered }
        .map { MleScoredRoute(it, score(it)) }
        .sortedWith(
            compareByDescending<MleScoredRoute> { it.score }
                .thenBy { it.candidate.latencyMs ?: Long.MAX_VALUE }
                .thenBy { it.candidate.id }
        )
        .toList()

    private fun score(route: MleRouteCandidate): Double {
        val success = route.successRate.coerceIn(0.0, 1.0)
        val latency = route.latencyMs?.let {
            1.0 / (1.0 + it.coerceAtLeast(0).toDouble() / 500.0)
        } ?: 0.0
        val throughput = route.throughputKbps?.let {
            (it.coerceAtLeast(0).toDouble() / 10_000.0).coerceAtMost(1.0)
        } ?: 0.0
        val verifiedBonus = if (route.endToEndVerified) 0.10 else 0.0
        val failurePenalty = route.consecutiveFailures.coerceAtLeast(0) * 0.12
        return success * 0.55 + latency * 0.25 + throughput * 0.10 +
            verifiedBonus - failurePenalty
    }
}
