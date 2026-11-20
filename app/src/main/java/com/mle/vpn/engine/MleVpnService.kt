package com.mle.vpn.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.mle.vpn.MainActivity
import com.mle.vpn.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android VPN service foundation.
 *
 * IMPORTANT: The TUN descriptor must not be established until a packet engine
 * is attached and ready to consume/produce packets. Establishing a TUN without
 * a packet pump would black-hole device traffic.
 */
class MleVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val stopping = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnelResources()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                stopping.set(false)
                startForeground(NOTIFICATION_ID, buildNotification("در انتظار راه‌اندازی موتور اتصال"))
                // The libbox CommandServer/packet engine adapter is not wired yet.
                // Do not establish a TUN here: that would interrupt all traffic.
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Called by the future libbox PlatformInterface.OpenTun adapter only after
     * the packet engine has been started and its configuration validated.
     */
    @Synchronized
    fun establishTun(config: TunConfig): Int {
        check(!stopping.get()) { "VPN service is stopping." }
        check(tun == null) { "A TUN interface is already active." }
        val builder = Builder()
            .setSession("MLE VPN")
            .setMtu(config.mtu)
            .addAddress("10.73.0.1", 30)
            .addRoute("0.0.0.0", 0)

        if (config.ipv6Enabled) {
            builder.addAddress("fd73:6d6c:6500::1", 126)
            builder.addRoute("::", 0)
        }
        config.dnsServers.forEach(builder::addDnsServer)
        config.allowedApplications.forEach { packageName ->
            builder.addAllowedApplication(packageName)
        }
        config.disallowedApplications.forEach { packageName ->
            builder.addDisallowedApplication(packageName)
        }

        val descriptor = checkNotNull(builder.establish()) {
            "Android did not grant a TUN interface."
        }
        tun = descriptor
        return descriptor.detachFd().also { tun = null }
    }

    fun protectTunnelSocket(fd: Int): Boolean = protect(fd)

    @Synchronized
    fun closeTun() {
        runCatching { tun?.close() }
        tun = null
    }

    override fun onRevoke() {
        stopping.set(true)
        stopTunnelResources()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnelResources()
        super.onDestroy()
    }

    @Synchronized
    private fun stopTunnelResources() {
        stopping.set(true)
        runCatching { tun?.close() }
        tun = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "وضعیت اتصال MLE VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mle_logo)
            .setContentTitle("MLE VPN")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.mle.vpn.action.START"
        const val ACTION_STOP = "com.mle.vpn.action.STOP"
        private const val CHANNEL_ID = "mle_vpn_status"
        private const val NOTIFICATION_ID = 7301
    }
}

data class TunConfig(
    val mtu: Int = 1500,
    val ipv6Enabled: Boolean = true,
    val dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
    val allowedApplications: List<String> = emptyList(),
    val disallowedApplications: List<String> = emptyList()
) {
    init {
        require(mtu in 1280..9000) { "MTU must be between 1280 and 9000." }
        require(dnsServers.isNotEmpty()) { "At least one DNS server is required." }
        require(allowedApplications.isEmpty() || disallowedApplications.isEmpty()) {
            "Allow-list and deny-list cannot be combined."
        }
    }
}
