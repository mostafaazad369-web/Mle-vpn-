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
