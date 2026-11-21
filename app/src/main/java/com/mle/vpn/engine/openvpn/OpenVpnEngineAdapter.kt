package com.mle.vpn.engine.openvpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.mle.vpn.engine.ConnectivityReport
import com.mle.vpn.engine.EngineAdapter
import com.mle.vpn.engine.EngineCatalog
import com.mle.vpn.engine.EngineResult
import com.mle.vpn.engine.MleConnectionPhase
import com.mle.vpn.engine.MleVpnService
import com.mle.vpn.engine.MleVpnStatus
import com.mle.vpn.profile.OpenVpnProfilePreflight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Adapter for the already-integrated OpenVPN 3/JNI runtime.
 *
 * The profile is preflighted before it reaches the service. Credentials are
 * passed only in the transient service Intent and are not persisted or logged.
 * VPN consent remains an Android Activity responsibility.
 */
class OpenVpnEngineAdapter(context: Context) : EngineAdapter {
    private val appContext = context.applicationContext

    override val descriptor =
        requireNotNull(EngineCatalog.find("openvpn")) {
            "openvpn must be registered in EngineCatalog"
        }

    override suspend fun prepare(): EngineResult {
        if (!MleOpenVpnNative.available()) {
            return EngineResult(false, "کتابخانه OpenVPN برای این معماری در دسترس نیست.")
        }
        return if (VpnService.prepare(appContext) == null) {
            EngineResult(true, "مجوز سیستمی VPN صادر شده است.")
        } else {
            EngineResult(false, "ابتدا مجوز VPN را از Activity و پنجره سیستمی اندروید تأیید کنید.")
        }
    }

    override suspend fun connect(profile: String): EngineResult =
        connect(profile, "", "")

    /**
     * Starts a preflighted .ovpn profile. Credentials are deliberately not
     * stored by this adapter; callers should obtain them from secure user input.
     */
    suspend fun connect(profile: String, username: String, password: String): EngineResult {
        if (profile.isBlank()) return EngineResult(false, "پروفایل OpenVPN خالی است.")
        val preflight = runCatching { OpenVpnProfilePreflight.inspect(profile) }
            .getOrElse { return EngineResult(false, it.message ?: "پروفایل OpenVPN معتبر نیست.") }
        if (preflight.requiresCredentials && (username.isBlank() || password.isBlank())) {
            return EngineResult(false, "این پروفایل به نام کاربری و گذرواژه نیاز دارد.")
        }

        val readiness = prepare()
        if (!readiness.success) return readiness

        return try {
            MleVpnStatus.starting()
            val startIntent = Intent(appContext, MleVpnService::class.java).apply {
                action = MleVpnService.ACTION_START_OPENVPN
                putExtra(MleVpnService.EXTRA_OPENVPN_PROFILE, profile)
                putExtra(MleVpnService.EXTRA_OPENVPN_USERNAME, username)
                putExtra(MleVpnService.EXTRA_OPENVPN_PASSWORD, password)
            }
            ContextCompat.startForegroundService(appContext, startIntent)

            val firstState = withTimeoutOrNull(15_000L) {
                MleVpnStatus.status.first {
                    it.phase in setOf(
                        MleConnectionPhase.STARTING,
                        MleConnectionPhase.TUNNEL_ACTIVE,
                        MleConnectionPhase.INTERNET_VERIFIED,
                        MleConnectionPhase.NO_INTERNET,
                        MleConnectionPhase.ERROR
                    )
                }
            } ?: return EngineResult(false, "سرویس OpenVPN در مهلت مقرر شروع نشد.")

            if (firstState.phase == MleConnectionPhase.ERROR) {
                return EngineResult(false, firstState.detail)
            }

            val finalState = if (firstState.phase == MleConnectionPhase.INTERNET_VERIFIED ||
                firstState.phase == MleConnectionPhase.NO_INTERNET
            ) firstState else withTimeoutOrNull(60_000L) {
                MleVpnStatus.status.first {
                    it.phase in setOf(
                        MleConnectionPhase.INTERNET_VERIFIED,
                        MleConnectionPhase.NO_INTERNET,
                        MleConnectionPhase.ERROR
                    )
                }
            } ?: run {
                disconnect()
                return EngineResult(
                    false,
                    "زمان تأیید اتصال OpenVPN تمام شد؛ درخواست توقف تونل ارسال شد."
                )
            }

            when (finalState.phase) {
                MleConnectionPhase.INTERNET_VERIFIED ->
                    EngineResult(true, finalState.detail, sessionId = "openvpn")
                MleConnectionPhase.NO_INTERNET ->
                    EngineResult(false, finalState.detail, sessionId = "openvpn")
                else -> EngineResult(false, finalState.detail)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message ?: "شروع OpenVPN ناموفق بود."
            MleVpnStatus.failed(message)
            EngineResult(false, message)
        }
    }

    override suspend fun disconnect(): EngineResult = try {
        appContext.startService(
            Intent(appContext, MleVpnService::class.java).apply {
                action = MleVpnService.ACTION_STOP
            }
        )
        EngineResult(true, "درخواست توقف OpenVPN ارسال شد.")
    } catch (error: Exception) {
        EngineResult(false, error.message ?: "توقف OpenVPN ناموفق بود.")
    }

    override suspend fun testConnectivity(): ConnectivityReport {
        val current = MleVpnStatus.status.value
        val tunnelUp = current.phase in setOf(
            MleConnectionPhase.TUNNEL_ACTIVE,
            MleConnectionPhase.INTERNET_VERIFIED,
            MleConnectionPhase.NO_INTERNET
        )
        return ConnectivityReport(
            tunnelEstablished = tunnelUp,
            internetReachable = current.phase == MleConnectionPhase.INTERNET_VERIFIED,
            detail = current.detail
        )
    }
}
