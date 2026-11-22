package com.mle.vpn.engine

/**
 * Contract implemented by a real packet engine (for example, a pinned
 * sing-box/libbox adapter). Implementations must own start/stop atomically,
 * protect their upstream sockets from VPN routing loops, and surface
 * verified connectivity separately from TUN establishment.
 */
interface TunnelEngine {
    val id: String
    suspend fun validate(configuration: EngineConfiguration): EngineValidation
    suspend fun start(configuration: EngineConfiguration): EngineStartResult
    suspend fun stop()
}

data class EngineConfiguration(
    val profileId: String,
    val serializedConfig: String,
)

sealed interface EngineValidation {
    data object Valid : EngineValidation
    data class Invalid(val reason: String) : EngineValidation
}

sealed interface EngineStartResult {
    data class Started(val sessionId: String) : EngineStartResult
    data class Failed(val reason: String) : EngineStartResult
}
