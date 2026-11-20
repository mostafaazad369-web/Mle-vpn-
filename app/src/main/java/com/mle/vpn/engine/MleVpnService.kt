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
    private val stopping = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engineManager: MleEngineManager
    private var trafficProbeJob: Job? = null
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
                    if (::engineManager.isInitialized) runCatching { engineManager.stop() }
                    stopTunnelResources()
                    MleVpnStatus.disconnected()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val engineId = intent.getStringExtra(EXTRA_ENGINE_ID) ?: "sing-box"
                if (config.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val rawConfigs = intent.getStringArrayListExtra(EXTRA_CONFIG_CANDIDATES).orEmpty()
                val rawProfileIds = intent.getStringArrayListExtra(EXTRA_PROFILE_IDS).orEmpty()
                val candidatePairs = rawConfigs.mapIndexedNotNull { index, candidate ->
                    candidate.takeIf { it.isNotBlank() }?.let {
                        it to (rawProfileIds.getOrNull(index) ?: "unknown-$index")
                    }
                }.distinctBy { it.first }.take(MAX_FAILOVER_CANDIDATES)
                val candidates = candidatePairs.map { it.first }.ifEmpty { listOf(config) }
                val profileIds = candidatePairs.map { it.second }.ifEmpty { listOf("unknown-0") }
                val healthStore = MleRouteHealthStore(this)
                stopping.set(false)
                trafficProbeJob?.cancel()
                configureUnderlyingNetwork()
                MleVpnStatus.starting()
                startForeground(NOTIFICATION_ID, buildNotification("در حال بررسی مسیرهای اتصال"))
                if (!::engineManager.isInitialized) {
                    val singBox = MleBoxRuntime(this, MlePlatformInterface(this))
                    engineManager = MleEngineManager(listOf(
                        SingBoxEngineAdapter(singBox),
                        XrayEngineAdapter(XrayRuntime(this))
                    ))
                }
                serviceScope.launch {
                    var connected = false
                    var lastFailure = "هیچ مسیر قابل استفاده‌ای پیدا نشد."
                    for ((index, candidateConfig) in candidates.withIndex()) {
                        if (stopping.get()) break
                        val profileId = profileIds.getOrNull(index) ?: "unknown-$index"
                        val attemptStartedAt = android.os.SystemClock.elapsedRealtime()
                        try {
                            MleVpnStatus.starting()
                            getSystemService(NotificationManager::class.java).notify(
                                NOTIFICATION_ID,
                                buildNotification("آزمایش مسیر ${index + 1} از ${candidates.size}")
                            )
                            engineManager.start(engineId, candidateConfig)
                            configureUnderlyingNetwork()
                            MleVpnStatus.tunnelActive()
                            // Give Android and the packet engine a moment to install
                            // routes before testing a real HTTPS request through TUN.
                            delay(2_000)
                            val failure = verifyInternetThroughVpn()
                            if (failure == null) {
                                connected = true
                                healthStore.recordSuccess("$engineId:$profileId", android.os.SystemClock.elapsedRealtime() - attemptStartedAt)
                                MleVpnStatus.internetVerified()
                                getSystemService(NotificationManager::class.java).notify(
                                    NOTIFICATION_ID,
                                    buildNotification("اینترنت از مسیر ${index + 1} تأیید شد")
                                )
                                break
                            }
                            lastFailure = failure
                            healthStore.recordFailure("$engineId:$profileId")
                            android.util.Log.w("MLE-VPN", "Candidate ${index + 1} failed end-to-end verification")
                            runCatching { engineManager.stop() }
                            stopTunnelResources(markStopping = false)
                        } catch (error: Exception) {
                            lastFailure = error.message ?: "راه‌اندازی مسیر ${index + 1} ناموفق بود."
                            healthStore.recordFailure("$engineId:$profileId")
                            android.util.Log.w("MLE-VPN", "Candidate ${index + 1} startup failed", error)
                            runCatching { engineManager.stop() }
                            stopTunnelResources(markStopping = false)
                        }
                    }
                    if (!connected) {
                        if (!stopping.get()) {
                            MleVpnStatus.trafficUnavailable(
                                "هیچ‌یک از ${candidates.size} مسیر آزمایشی اینترنت نداد. آخرین خطا: $lastFailure"
                            )
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        return@launch
                    }
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
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Capture the physical network before Android installs this VPN as the
     * process default. This gives sing-box a real upstream network to protect
     * and prevents the VPN from selecting its own TUN as the proxy route.
     */
    internal fun configureUnderlyingNetwork(): String? {
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
        return selectedUnderlyingNetwork?.let { manager.getLinkProperties(it)?.interfaceName }
    }

    /** Resolve proxy hostnames on the captured physical network before the VPN TUN is installed. */
    internal fun resolveUnderlyingHost(host: String): String {
        require(host.isNotBlank()) { "Proxy server hostname is empty." }
        val network = checkNotNull(selectedUnderlyingNetwork) {
            "Physical network is not available for proxy DNS bootstrap."
        }
        val resolved = network.getAllByName(host).firstOrNull()
            ?: error("آدرس سرور از طریق شبکه اصلی قابل resolve نیست.")
        return resolved.hostAddress?.substringBefore("%")
            ?: error("نشانی IP سرور قابل استفاده نیست.")
    }

    /** Establishes the Android TUN used by Xray's native tun inbound. */
    internal fun establishXrayTun(): ParcelFileDescriptor {
        check(!stopping.get()) { "VPN service is stopping." }
        check(tun == null) { "A TUN interface is already active." }
        check(VpnService.prepare(this) == null) { "VPN permission has not been granted." }
        val builder = Builder()
            .setSession("MLE VPN • Xray")
            .setMtu(1500)
            .addAddress("10.73.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("1.0.0.1")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.addAddress("fd73:6d6c:6500::1", 126)
            .addRoute("::", 0)
        return checkNotNull(builder.establish()) {
            "Android did not grant an Xray TUN interface."
        }
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
        trafficProbeJob?.cancel()
        serviceScope.launch {
            if (::engineManager.isInitialized) runCatching { engineManager.stop() }
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
        trafficProbeJob?.cancel()
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            if (::engineManager.isInitialized) runCatching { engineManager.stop() }
            stopTunnelResources()
            if (MleVpnStatus.status.value.phase !in setOf(
                    MleConnectionPhase.NO_INTERNET, MleConnectionPhase.ERROR
                )) {
                MleVpnStatus.disconnected()
            }
        }
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    @Synchronized
    private fun stopTunnelResources(markStopping: Boolean = true) {
        if (markStopping) stopping.set(true)
        runCatching { tun?.close() }
        tun = null
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
        const val EXTRA_CONFIG = "com.mle.vpn.extra.CONFIG"
        const val EXTRA_ENGINE_ID = "com.mle.vpn.extra.ENGINE_ID"
        const val EXTRA_CONFIG_CANDIDATES = "com.mle.vpn.extra.CONFIG_CANDIDATES"
        const val EXTRA_PROFILE_IDS = "com.mle.vpn.extra.PROFILE_IDS"
        private const val MAX_FAILOVER_CANDIDATES = 3
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
