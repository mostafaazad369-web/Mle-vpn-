package com.mle.vpn.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * EngineAdapter implementation for MLE's existing sing-box/libbox service.
 *
 * The profile argument is the validated JSON configuration produced by
 * SingBoxConfigBuilder. Android VPN consent remains an Activity responsibility:
 * this adapter never tries to bypass the system permission dialog.
 */
class SingBoxEngineAdapter(context: Context) : EngineAdapter {
    private val appContext = context.applicationContext

    override val descriptor: EngineDescriptor =
        requireNotNull(EngineCatalog.find("sing_box")) {
            "sing_box must be registered in EngineCatalog"
        }

    override suspend fun prepare(): EngineResult {
        return if (VpnService.prepare(appContext) == null) {
            EngineResult(true, "اجازه سیستمی VPN قبلاً صادر شده است.")
        } else {
            EngineResult(
                false,
                "ابتدا باید پنجره اجازه اتصال VPN را از رابط کاربری اندروید تأیید کنید."
            )
        }
    }

    override suspend fun connect(profile: String): EngineResult {
        if (profile.isBlank()) {
            return EngineResult(false, "پروفایل یا پیکربندی sing-box خالی است.")
        }
        if (VpnService.prepare(appContext) != null) {
            return EngineResult(
                false,
                "اجازه VPN صادر نشده است؛ درخواست مجوز باید از Activity نمایش داده شود."
            )
        }

        return try {
            val startIntent = Intent(appContext, MleVpnService::class.java).apply {
                action = MleVpnService.ACTION_START
                putExtra(MleVpnService.EXTRA_CONFIG, profile)
            }
            ContextCompat.startForegroundService(appContext, startIntent)

            val startup = withTimeoutOrNull(10_000) {
                MleVpnStatus.status.first {
                    it.phase == MleConnectionPhase.STARTING ||
                        it.phase == MleConnectionPhase.ERROR
                }
            } ?: return EngineResult(false, "سرویس VPN در مهلت مقرر شروع نشد.")

            if (startup.phase == MleConnectionPhase.ERROR) {
                return EngineResult(false, startup.detail)
            }

            val verified = withTimeoutOrNull(45_000) {
                MleVpnStatus.status.first {
                    it.phase == MleConnectionPhase.INTERNET_VERIFIED ||
                        it.phase == MleConnectionPhase.ERROR
                }
            } ?: return EngineResult(
                false,
                "راه‌اندازی تونل آغاز شد اما عبور واقعی ترافیک اینترنت هنوز تأیید نشده است."
            )

            if (verified.phase == MleConnectionPhase.INTERNET_VERIFIED) {
                EngineResult(true, verified.detail, sessionId = "sing_box")
            } else {
                EngineResult(false, verified.detail)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            EngineResult(
                false,
                error.message ?: "ارسال درخواست شروع sing-box ناموفق بود."
            )
        }
    }

    override suspend fun disconnect(): EngineResult = try {
        appContext.startService(
            Intent(appContext, MleVpnService::class.java).apply {
                action = MleVpnService.ACTION_STOP
            }
        )
        EngineResult(true, "درخواست قطع اتصال sing-box ارسال شد.")
    } catch (error: Exception) {
        EngineResult(false, error.message ?: "قطع اتصال sing-box ناموفق بود.")
    }

    override suspend fun testConnectivity(): ConnectivityReport {
        val current = MleVpnStatus.status.value
        val tunnelIsUp = current.phase == MleConnectionPhase.TUNNEL_ACTIVE ||
            current.phase == MleConnectionPhase.INTERNET_VERIFIED ||
            current.phase == MleConnectionPhase.NO_INTERNET

        return ConnectivityReport(
            tunnelEstablished = tunnelIsUp,
            internetReachable = current.phase == MleConnectionPhase.INTERNET_VERIFIED,
            detail = current.detail
        )
    }
}
