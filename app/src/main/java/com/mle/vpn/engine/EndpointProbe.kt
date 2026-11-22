package com.mle.vpn.engine

import com.mle.vpn.profile.ImportedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import android.os.SystemClock

/**
 * A bounded TCP reachability check for a profile endpoint.
 *
 * This is only a server/port preflight. It does not perform TLS, proxy
 * authentication, a sing-box handshake, or an end-to-end VPN traffic test.
 */
data class EndpointProbeResult(
    val reachable: Boolean,
    val latencyMs: Long?,
    val message: String
)

object EndpointProbe {
    private const val CONNECT_TIMEOUT_MS = 4_000

    suspend fun check(profile: ImportedProfile): EndpointProbeResult = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(profile.host, profile.port),
                    CONNECT_TIMEOUT_MS
                )
            }
            EndpointProbeResult(
                reachable = true,
                latencyMs = SystemClock.elapsedRealtime() - startedAt,
                message = "درگاه TCP پاسخ داد"
            )
        } catch (_: UnknownHostException) {
            EndpointProbeResult(false, null, "نام میزبان پیدا نشد")
        } catch (_: SocketTimeoutException) {
            EndpointProbeResult(false, null, "پاسخی در مهلت ۴ ثانیه دریافت نشد")
        } catch (_: IOException) {
            EndpointProbeResult(false, null, "اتصال TCP به درگاه برقرار نشد")
        } catch (_: SecurityException) {
            EndpointProbeResult(false, null, "دسترسی شبکه مجاز نیست")
        }
    }
}
