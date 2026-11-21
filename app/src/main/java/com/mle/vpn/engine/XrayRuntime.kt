package com.mle.vpn.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import pg.Seq
import org.json.JSONObject
import java.util.UUID

/**
 * Xray's libv2ray controller. The Android VpnService owns the TUN descriptor;
 * Xray duplicates and consumes its fd through CoreController.startLoop(config, fd).
 */
class XrayRuntime(private val service: MleVpnService) {
    @Volatile private var controller: CoreController? = null

    suspend fun start(rawConfig: String) = withContext(Dispatchers.IO) {
        check(controller?.isRunning != true) { "Xray is already running." }
        val upstreamInterface = service.configureUnderlyingNetwork()
            ?: error("رابط شبکه اصلی برای خروجی Xray شناسایی نشد.")
        val config = JSONObject(rawConfig).put("autoOutboundsInterface", upstreamInterface)
        resolveProxyBeforeTun(config)
        val descriptor = service.establishXrayTun()
        service.holdTunDescriptor(descriptor)
        try {
            initializeCoreEnvironment()
            val next = Libv2ray.newCoreController(Callback)
            next.startLoop(config.toString(), descriptor.fd)
            check(next.isRunning) { "Xray core did not enter the running state." }
            controller = next
            Log.i(TAG, "Xray core started; upstream=$upstreamInterface")
        } catch (error: Exception) {
            runCatching { service.closeTun() }
            throw error
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val current = controller
        controller = null
        if (current != null) {
            runCatching { current.stopLoop() }
                .onFailure { Log.w(TAG, "Xray stopLoop failed", it) }
        }
        service.closeTun()
    }

    /**
     * Resolve the proxy endpoint using Android's captured physical Network before
     * installing the VPN TUN. Keep the original hostname as TLS SNI/HTTP host.
     */
    private fun resolveProxyBeforeTun(config: JSONObject) {
        val outbounds = config.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .asSequence()
            .map { outbounds.getJSONObject(it) }
            .first { it.optString("tag") == "proxy" }
        val protocol = proxy.getString("protocol")
        val settings = proxy.getJSONObject("settings")
        val server = when (protocol) {
            "vless", "vmess" -> settings.getJSONArray("vnext").getJSONObject(0)
            "trojan" -> settings.getJSONArray("servers").getJSONObject(0)
            else -> error("Unsupported Xray outbound for DNS bootstrap: $protocol")
        }
        val hostname = server.getString("address")
        val ipAddress = service.resolveUnderlyingHost(hostname)
        server.put("address", ipAddress)

        val stream = proxy.optJSONObject("streamSettings")
        if (stream?.optString("security") == "tls") {
            val tls = stream.optJSONObject("tlsSettings") ?: JSONObject().also {
                stream.put("tlsSettings", it)
            }
            if (tls.optString("serverName").isBlank()) tls.put("serverName", hostname)
        }
    }

    private fun initializeCoreEnvironment() {
        synchronized(XrayRuntime::class.java) {
            if (initialized) return
            Seq.setContext(service.applicationContext)
            val prefs = service.getSharedPreferences("mle_xray_runtime", 0)
            val key = prefs.getString("xudp_key", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("xudp_key", it).apply()
            }
            Libv2ray.initCoreEnv(service.filesDir.absolutePath, key)
            initialized = true
        }
    }

    private object Callback : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.i(TAG, "Xray status=$code message=${message.orEmpty().take(180)}")
            return 0L
        }
    }

    companion object {
        private const val TAG = "MLE-XRAY"
        @Volatile private var initialized = false
    }
}
