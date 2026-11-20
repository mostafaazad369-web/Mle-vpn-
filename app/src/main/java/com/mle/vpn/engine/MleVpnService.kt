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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var boxRuntime: MleBoxRuntime

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping.set(true)
                serviceScope.launch {
                    if (::boxRuntime.isInitialized) runCatching { boxRuntime.stop() }
                    stopTunnelResources()
                    MleVpnStatus.disconnected()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                stopping.set(false)
                MleVpnStatus.starting()
                startForeground(NOTIFICATION_ID, buildNotification("در حال راه‌اندازی موتور sing-box"))
                if (!::boxRuntime.isInitialized) {
                    boxRuntime = MleBoxRuntime(this, MlePlatformInterface(this))
                }
                serviceScope.launch {
                    runCatching { boxRuntime.start(config) }
                        .onSuccess {
                            MleVpnStatus.tunnelActive()
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, buildNotification("تونل VPN فعال شد"))
                        }
                        .onFailure { error ->
                            android.util.Log.e("MLE-VPN", "sing-box startup failed", error)
                            MleVpnStatus.failed(error.message ?: "راه‌اندازی موتور VPN ناموفق بود")
                            runCatching { boxRuntime.stop() }
                            stopTunnelResources()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                }
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Called by the libbox PlatformInterface.openTun adapter only after
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

    @Synchronized
    internal fun holdTunDescriptor(descriptor: ParcelFileDescriptor) {
        check(!stopping.get()) { "VPN service is stopping." }
        runCatching { tun?.close() }
        tun = descriptor
    }

    internal fun requestEngineStop() {
        stopping.set(true)
        serviceScope.launch {
            if (::boxRuntime.isInitialized) runCatching { boxRuntime.stop() }
            stopTunnelResources()
            MleVpnStatus.disconnected()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun protectTunnelSocket(fd: Int): Boolean = protect(fd)

    @Synchronized
    fun closeTun() {
        runCatching { tun?.close() }
        tun = null
    }

    override fun onRevoke() {
        requestEngineStop()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopping.set(true)
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            if (::boxRuntime.isInitialized) runCatching { boxRuntime.stop() }
            stopTunnelResources()
            MleVpnStatus.disconnected()
        }
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
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
        const val EXTRA_CONFIG = "com.mle.vpn.extra.CONFIG"
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
        require(dnsServers.all(::isIpLiteral)) {
            "DNS server entries must be valid IPv4 or IPv6 literals, not hostnames."
        }
        require(allowedApplications.isEmpty() || disallowedApplications.isEmpty()) {
            "Allow-list and deny-list cannot be combined."
        }
        val packageNamePattern = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")
        require((allowedApplications + disallowedApplications).all(packageNamePattern::matches)) {
            "Application filters must contain valid package names."
        }
        require(allowedApplications.distinct().size == allowedApplications.size) {
            "Allow-list contains duplicate package names."
        }
        require(disallowedApplications.distinct().size == disallowedApplications.size) {
            "Deny-list contains duplicate package names."
        }
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.isBlank() || value.length > 45 || value.any { !(it.isDigit() || it == '.' || it == ':') }) {
            return false
        }
        val parsed = runCatching { InetAddress.getByName(value) }.getOrNull()
        return parsed is Inet4Address || parsed is Inet6Address
    }
}
