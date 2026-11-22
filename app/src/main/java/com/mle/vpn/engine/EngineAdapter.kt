package com.mle.vpn.engine

/**
 * Contract for real VPN engine integrations.
 * Implementations must report success only after the Android VPN interface is
 * established and an end-to-end connectivity check has passed.
 */
interface EngineAdapter {
    val descriptor: EngineDescriptor
    suspend fun prepare(): EngineResult
    suspend fun connect(profile: String): EngineResult
    suspend fun disconnect(): EngineResult
    suspend fun testConnectivity(): ConnectivityReport
}

data class EngineResult(
    val success: Boolean,
    val message: String,
    val sessionId: String? = null
)

data class ConnectivityReport(
    val tunnelEstablished: Boolean,
    val internetReachable: Boolean,
    val exitIp: String? = null,
    val latencyMs: Long? = null,
    val detail: String? = null
)
