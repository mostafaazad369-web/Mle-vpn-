package com.mle.vpn.engine

import android.os.SystemClock
import com.mle.vpn.profile.ImportedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A bounded TCP reachability check for a profile endpoint.
 *
 * This is only a server/port preflight. It does not perform TLS, proxy
 * authentication, a sing-box handshake, or an end-to-end VPN traffic test.
 * Only globally routable IP addresses are contacted; private, local, and
 * special-use destinations from untrusted imported profiles are rejected.
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
            require(profile.port in 1..65535) { "Invalid endpoint port" }
            val publicAddress = InetAddress.getAllByName(profile.host)
                .firstOrNull(::isPubliclyRoutable)
                ?: return@withContext EndpointProbeResult(
                    false, null, "آزمون فقط برای IP عمومی مجاز است؛ آدرس محلی یا خصوصی رد شد."
                )

            // Connect to the exact validated DNS result, not the hostname, so a
            // second DNS lookup cannot rebind the destination to a private IP.
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(publicAddress, profile.port),
                    CONNECT_TIMEOUT_MS
                )
            }
            EndpointProbeResult(
                reachable = true,
                latencyMs = SystemClock.elapsedRealtime() - startedAt,
                message = "درگاه TCP روی IP عمومی پاسخ داد"
            )
        } catch (_: UnknownHostException) {
            EndpointProbeResult(false, null, "نام میزبان پیدا نشد")
        } catch (_: SocketTimeoutException) {
            EndpointProbeResult(false, null, "پاسخی در مهلت ۴ ثانیه دریافت نشد")
        } catch (_: IOException) {
            EndpointProbeResult(false, null, "اتصال TCP به درگاه برقرار نشد")
        } catch (_: IllegalArgumentException) {
            EndpointProbeResult(false, null, "آدرس یا درگاه سرور معتبر نیست")
        } catch (_: SecurityException) {
            EndpointProbeResult(false, null, "دسترسی شبکه مجاز نیست")
        }
    }

    /** True only for globally-routable unicast address space. */
    internal fun isPubliclyRoutable(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false

        val bytes = address.address
        if (address is Inet4Address) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            val c = bytes[2].toInt() and 0xff
            return when {
                a == 0 || a == 10 || a == 127 -> false
                a == 100 && b in 64..127 -> false // Shared address space (100.64/10)
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && b == 0 && (c == 0 || c == 2) -> false
                a == 192 && b == 168 -> false
                a == 192 && b == 88 && c == 99 -> false
                a == 198 && (b == 18 || b == 19) -> false
                a == 198 && b == 51 && c == 100 -> false
                a == 203 && b == 0 && c == 113 -> false
                a >= 224 -> false // Multicast, reserved, and broadcast space
                else -> true
            }
        }

        // Permit global-unicast IPv6 (2000::/3) only. This excludes ULA,
        // link-local, loopback, IPv4-mapped and other special-purpose ranges.
        if (bytes.size != 16 || (bytes[0].toInt() and 0xe0) != 0x20) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff

        // Exclude IETF protocol assignments (2001::/23), the deprecated
        // 6to4 transition block (2002::/16), and documentation (3fff::/20).
        // These prefixes sit inside 2000::/3 but are not ordinary public
        // unicast endpoints suitable for an imported-profile TCP probe.
        val isIetfProtocolAssignment = first == 0x20 && second == 0x01 && (third and 0xfe) == 0
        val isSixToFour = first == 0x20 && second == 0x02
        val isDocumentation = first == 0x3f && second == 0xff && (third and 0xf0) == 0
        return !(isIetfProtocolAssignment || isSixToFour || isDocumentation)
    }
}
