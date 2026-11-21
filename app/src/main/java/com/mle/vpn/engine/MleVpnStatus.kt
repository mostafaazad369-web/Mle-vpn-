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
    val detail: String = "اتصال برقرار نیست",
    val bytesOut: Long = 0L,
    val bytesIn: Long = 0L,
    val trafficStatsAvailable: Boolean = false
)

/**
 * In-process connection state shared by the VPN service and Compose UI.
 * TUNNEL_ACTIVE means the selected packet engine started and Android TUN was opened;
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
        mutableStatus.value = mutableStatus.value.copy(
            phase = MleConnectionPhase.TUNNEL_ACTIVE,
            detail = "تونل VPN فعال است؛ دسترسی به سرور و اینترنت هنوز جداگانه تأیید نشده است."
        )
    }

    fun internetVerified() {
        mutableStatus.value = mutableStatus.value.copy(
            phase = MleConnectionPhase.INTERNET_VERIFIED,
            detail = "عبور ترافیک و دسترسی HTTPS از داخل VPN تأیید شد."
        )
    }

    fun trafficUnavailable(message: String) {
        mutableStatus.value = mutableStatus.value.copy(
            phase = MleConnectionPhase.NO_INTERNET,
            detail = message.ifBlank { "تونل فعال است اما دسترسی اینترنت از داخل VPN تأیید نشد." }
        )
    }

    /** Publishes monotonic engine counters for the current session; values are never logged here. */
    fun updateTraffic(bytesOut: Long, bytesIn: Long) {
        mutableStatus.value = mutableStatus.value.copy(
            bytesOut = bytesOut.coerceAtLeast(0L),
            bytesIn = bytesIn.coerceAtLeast(0L),
            trafficStatsAvailable = true
        )
    }

    fun disconnected() {
        mutableStatus.value = MleConnectionStatus(
            MleConnectionPhase.DISCONNECTED,
            "اتصال قطع است"
        )
    }

    fun failed(message: String) {
        mutableStatus.value = mutableStatus.value.copy(
            phase = MleConnectionPhase.ERROR,
            detail = message.ifBlank { "راه‌اندازی VPN ناموفق بود" }
        )
    }
}
