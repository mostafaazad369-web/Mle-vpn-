package com.mle.vpn.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.net.Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.mle.vpn.MainActivity
import com.mle.vpn.R
import com.mle.vpn.engine.openvpn.OpenVpnTunConfig
import com.mle.vpn.engine.openvpn.MleOpenVpnRuntime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Android VPN service foundation.
 *
 * IMPORTANT: The TUN descriptor must not be established until a packet engine
 * is attached and ready to consume/produce packets. Establishing a TUN without
 * a packet pump would black-hole device traffic.
 */
class MleVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    // OpenVPN keeps the service-owned descriptor while native core receives a detached duplicate.
    private var openVpnTun: ParcelFileDescriptor? = null
    private val stopping = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var boxRuntime: MleBoxRuntime
    private var trafficProbeJob: Job? = null
    @Volatile private var engineLease: MleVpnEngineArbiter.Lease? = null
    @Volatile private var openVpnRuntime: MleOpenVpnRuntime? = null
    @Volatile private var selectedUnderlyingNetwork: Network? = null
    internal val currentUnderlyingNetwork: Network? get() = selectedUnderlyingNetwork

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping.set(true)
                trafficProbeJob?.cancel()
                serviceScope.launch {
                    if (::boxRuntime.isInitialized) runCatching { boxRuntime.stop() }
                    val openVpn = openVpnRuntime
                    if (openVpn != null) {
                        openVpn.stop()
                        return@launch
                    }
                    stopTunnelResources()
                    releaseEngineLease()
                    MleVpnStatus.disconnected()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_START_OPENVPN -> startOpenVpn(intent, startId)
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val lease = runCatching { MleVpnEngineArbiter.acquire(MleVpnEngine.SING_BOX) }.getOrElse { error ->
                    MleVpnStatus.failed(error.message ?: "موتور دیگری در حال استفاده از تونل VPN است")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                engineLease = lease
                stopping.set(false)
                trafficProbeJob?.cancel()
                configureUnderlyingNetwork()
                MleVpnStatus.starting()
                startForeground(NOTIFICATION_ID, buildNotification("در حال راه‌اندازی موتور sing-box"))
                if (!::boxRuntime.isInitialized) {
                    boxRuntime = MleBoxRuntime(this, MlePlatformInterface(this))
                }
                serviceScope.launch {
                    runCatching { boxRuntime.start(config) }
                        .onSuccess {
                            // The VPN TUN is now registered; publish the physical
                            // network as its underlay before probing app traffic.
                            configureUnderlyingNetwork()
                            MleVpnStatus.tunnelActive()
                            trafficProbeJob?.cancel()
                            trafficProbeJob = serviceScope.launch {
                                while (isActive && !stopping.get()) {
                                    val failure = verifyInternetThroughVpn()
                                    if (failure == null) {
                                        MleVpnStatus.internetVerified()
                                        getSystemService(NotificationManager::class.java).notify(
                                            NOTIFICATION_ID, buildNotification("اینترنت از داخل VPN تأیید شد")
                                        )
                                    } else {
                                        MleVpnStatus.trafficUnavailable(failure)
                                        getSystemService(NotificationManager::class.java).notify(
                                            NOTIFICATION_ID, buildNotification("تونل فعال است؛ ترافیک اینترنت عبور نمی‌کند")
                                        )
                                    }
                                    delay(15_000)
                                }
                            }
                        }
                        .onFailure { error ->
                            android.util.Log.e("MLE-VPN", "sing-box startup failed", error)
                            MleVpnStatus.failed(error.message ?: "راه‌اندازی موتور VPN ناموفق بود")
                            runCatching { boxRuntime.stop() }
                            stopTunnelResources()
                            releaseEngineLease()
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
     * Starts OpenVPN 3 through the shared Android VpnService and engine arbiter.
     * Credentials are consumed from the start Intent and are never persisted here.
     */
    private fun startOpenVpn(intent: Intent, startId: Int) {
        if (openVpnRuntime != null) {
            MleVpnStatus.failed("موتور OpenVPN از قبل در حال اجرا یا توقف است")
            stopSelf(startId)
            return
        }
        val profile = intent.getStringExtra(EXTRA_OPENVPN_PROFILE)
        val username = intent.getStringExtra(EXTRA_OPENVPN_USERNAME).orEmpty()
        val password = intent.getStringExtra(EXTRA_OPENVPN_PASSWORD).orEmpty()
        if (profile.isNullOrBlank()) {
            MleVpnStatus.failed("پروفایل OpenVPN خالی است")
            stopSelf(startId)
            return
        }
        if (!com.mle.vpn.engine.openvpn.MleOpenVpnNative.available()) {
            MleVpnStatus.failed("کتابخانه OpenVPN برای این معماری در APK موجود نیست")
            stopSelf(startId)
            return
        }
        val lease = runCatching { MleVpnEngineArbiter.acquire(MleVpnEngine.OPENVPN) }.getOrElse { error ->
            MleVpnStatus.failed(error.message ?: "موتور دیگری مالک تونل VPN است")
            stopSelf(startId)
            return
        }
        engineLease = lease
        stopping.set(false)
        trafficProbeJob?.cancel()
        configureUnderlyingNetwork()
        MleVpnStatus.starting()
        startForeground(NOTIFICATION_ID, buildNotification("در حال راه‌اندازی OpenVPN 3"))
        val runtime = MleOpenVpnRuntime(
            service = this,
            onConnected = {
                if (!stopping.get()) {
                    MleVpnStatus.tunnelActive()
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID, buildNotification("تونل OpenVPN متصل شد")
                    )
                }
            },
            onFailure = { message ->
                android.util.Log.e("MLE-VPN", message)
                MleVpnStatus.failed(message)
                stopping.set(true)
            },
            onFinished = {
                openVpnRuntime = null
                stopTunnelResources()
                releaseEngineLease()
                if (MleVpnStatus.status.value.phase != MleConnectionPhase.ERROR) {
                    if (stopping.get()) MleVpnStatus.disconnected()
                    else MleVpnStatus.failed("اتصال OpenVPN پایان یافت")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
        openVpnRuntime = runtime
        if (!runtime.start(profile, username, password)) {
            openVpnRuntime = null
            stopTunnelResources()
            releaseEngineLease()
            MleVpnStatus.failed("موتور OpenVPN نتوانست اجرا شود")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    /**
     * Capture the physical network before Android installs this VPN as the
     * process default. This gives sing-box a real upstream network to protect
     * and prevents the VPN from selecting its own TUN as the proxy route.
     */
    private fun configureUnderlyingNetwork() {
        val manager = getSystemService(ConnectivityManager::class.java)
        val candidate = manager.activeNetwork?.takeIf { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true
        } ?: manager.allNetworks.firstOrNull { network ->
            val caps = manager.getNetworkCapabilities(network)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        val applied = setUnderlyingNetworks(candidate?.let { arrayOf(it) })
        selectedUnderlyingNetwork = candidate.takeIf { applied }
        android.util.Log.i(
            "MLE-VPN",
            "Underlying network configured: network=$candidate applied=$applied"
        )
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

    /**
     * Establishes an OpenVPN TUN from server-pushed settings. The service retains
     * the original descriptor; native code receives a detached duplicate and owns
     * that duplicate until the core has stopped.
     *
     * This is a service primitive only. No OpenVPN start action is wired until the
     * native core, arbiter lease and callback lifecycle are implemented together.
     */
    @Synchronized
    internal fun establishOpenVpnTun(config: OpenVpnTunConfig): Int {
        check(!stopping.get()) { "VPN service is stopping." }
        check(tun == null && openVpnTun == null) { "A VPN TUN interface is already active." }
        val builder = Builder()
            .setSession("MLE VPN · OpenVPN")
            .setBlocking(false)
            .setMtu(config.mtu)
        config.addresses.forEach { builder.addAddress(it.address, it.prefixLength) }
        config.routes.forEach { builder.addRoute(it.address, it.prefixLength) }
        config.dnsServers.forEach(builder::addDnsServer)
        config.searchDomains.forEach(builder::addSearchDomain)

        val retained = checkNotNull(builder.establish()) {
            "Android did not grant an OpenVPN TUN interface."
        }
        var nativeDuplicate: ParcelFileDescriptor? = null
        try {
            val duplicate = ParcelFileDescriptor.dup(retained.fileDescriptor)
            nativeDuplicate = duplicate
            openVpnTun = retained
            return duplicate.detachFd()
        } catch (error: Exception) {
            runCatching { nativeDuplicate?.close() }
            runCatching { retained.close() }
            throw error
        }
    }

    @Synchronized
    internal fun holdTunDescriptor(descriptor: ParcelFileDescriptor) {
        check(!stopping.get()) { "VPN service is stopping." }
        runCatching { tun?.close() }
        tun = descriptor
    }

    internal fun requestEngineStop() {
        stopping.set(true)
        trafficProbeJob?.cancel()
        serviceScope.launch {
            if (::boxRuntime.isInitialized) runCatching { boxRuntime.stop() }
            val openVpn = openVpnRuntime
            if (openVpn != null) {
                openVpn.stop()
                return@launch
            }
            stopTunnelResources()
            releaseEngineLease()
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
        runCatching { openVpnTun?.close() }
        openVpnTun = null
    }

    override fun onRevoke() {
        requestEngineStop()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopping.set(true)
        trafficProbeJob?.cancel()
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            if (::boxRuntime.isInitialized) runCatching { boxRuntime.stop() }
            val openVpn = openVpnRuntime
            val stopped = openVpn?.stopAndAwait() ?: true
            if (stopped) {
                openVpnRuntime = null
                stopTunnelResources()
                releaseEngineLease()
                MleVpnStatus.disconnected()
            } else {
                // Do not close a descriptor that the native worker may still be
                // using. Its onFinished callback owns final cleanup after run().
                android.util.Log.w("MLE-VPN", "OpenVPN worker did not stop before service teardown timeout; deferring TUN cleanup.")
            }
        }
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    @Synchronized
    private fun releaseEngineLease() {
        MleVpnEngineArbiter.release(engineLease)
        engineLease = null
    }

    @Synchronized
    private fun stopTunnelResources() {
        stopping.set(true)
        runCatching { tun?.close() }
        tun = null
        runCatching { openVpnTun?.close() }
        openVpnTun = null
    }

    /**
     * Performs a real HTTPS request from this app process while the VPN is active.
     * Because the app is included in the Android VPN, this checks the actual
     * TUN -> sing-box outbound -> remote server -> internet path, not just TUN setup.
     * Returns null on success or a diagnostic message on failure.
     */
    private fun verifyInternetThroughVpn(): String? {
        val endpoints = listOf(
            "https://cp.cloudflare.com/generate_204",
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://www.cloudflare.com/cdn-cgi/trace"
        )
        val failures = mutableListOf<String>()
        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    instanceFollowRedirects = false
                    useCaches = false
                }
                val code = connection.responseCode
                if (code in 200..399) return null
                failures += "${URL(endpoint).host}: HTTP $code"
            } catch (error: Exception) {
                failures += "${URL(endpoint).host}: ${error.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }
        }
        return "تونل فعال است اما آزمون HTTPS از داخل VPN ناموفق بود: ${failures.joinToString("؛ ")}"
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
        const val ACTION_START_OPENVPN = "com.mle.vpn.action.START_OPENVPN"
        const val EXTRA_OPENVPN_PROFILE = "com.mle.vpn.extra.OPENVPN_PROFILE"
        const val EXTRA_OPENVPN_USERNAME = "com.mle.vpn.extra.OPENVPN_USERNAME"
        const val EXTRA_OPENVPN_PASSWORD = "com.mle.vpn.extra.OPENVPN_PASSWORD"
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
