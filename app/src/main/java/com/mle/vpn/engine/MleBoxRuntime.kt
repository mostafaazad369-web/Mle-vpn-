package com.mle.vpn.engine

import android.util.Log
import com.mle.vpn.MleApplication
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the sing-box CommandServer lifecycle for one VPN service instance.
 */
class MleBoxRuntime(
    private val service: MleVpnService,
    private val platform: MlePlatformInterface
) : CommandServerHandler {
    private var commandServer: CommandServer? = null
    private var activeConfig: String? = null
    private val lifecycleMutex = Mutex()

    suspend fun start(configJson: String) = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            require(configJson.isNotBlank()) { "VPN configuration is empty." }
            (service.application as MleApplication).ensureLibboxSetup()
            commandServer?.let { running ->
                if (activeConfig != configJson) {
                    running.startOrReloadService(configJson, OverrideOptions())
                    activeConfig = configJson
                }
                return@withLock
            }
            Libbox.promoteOOMDraft()
            Libbox.discardPowerReportDraft()
            val server = CommandServer(this@MleBoxRuntime, platform)
            try {
                server.start()
                server.startOrReloadService(configJson, OverrideOptions())
                commandServer = server
                activeConfig = configJson
            } catch (error: Throwable) {
                runCatching { server.closeService() }
                runCatching { server.close() }
                throw error
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val server = commandServer ?: return@withLock
            commandServer = null
            activeConfig = null
            runCatching { server.closeService() }
            runCatching { server.close() }
            Libbox.discardPowerReportDraft()
        }
    }

    override fun serviceStop() {
        service.requestEngineStop()
    }

    override fun serviceReload() {
        Log.w(TAG, "Reload requested without a fresh profile snapshot.")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        Log.i(TAG, "System proxy toggle ignored: Android VPN TUN mode is active.")
    }

    override fun triggerNativeCrash() {
        Log.w(TAG, "Native crash callback ignored in release-safe MLE runtime.")
    }

    override fun writeDebugMessage(message: String?) {
        Log.d(TAG, message.orEmpty())
    }

    override fun connectSSHAgent(): Int = -1

    companion object {
        private const val TAG = "MLE-BoxRuntime"
    }
}
