package com.mle.vpn.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes engine lifecycle operations and keeps tunnel establishment
 * distinct from verified internet connectivity.
 *
 * The Android service remains the only owner of the TUN descriptor. An
 * engine adapter must not report Started until its packet-forwarding path
 * is running and its upstream sockets are protected from VPN routing loops.
 */
class TunnelSessionController(
    private val engine: TunnelEngine,
) {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = mutableState.asStateFlow()

    suspend fun connect(configuration: EngineConfiguration): Boolean =
        lifecycleMutex.withLock {
            if (mutableState.value !is TunnelState.Disconnected) return@withLock false

            mutableState.value = TunnelState.Starting
            try {
                when (val validation = engine.validate(configuration)) {
                    EngineValidation.Valid -> Unit
                    is EngineValidation.Invalid -> {
                        mutableState.value = TunnelState.Error(validation.reason)
                        return@withLock false
                    }
                }

                when (val result = engine.start(configuration)) {
                    is EngineStartResult.Started -> {
                        mutableState.value = TunnelState.TunnelActive(result.sessionId)
                        true
                    }
                    is EngineStartResult.Failed -> {
                        mutableState.value = TunnelState.Error(result.reason)
                        false
                    }
                }
            } catch (failure: Exception) {
                mutableState.value = TunnelState.Error(
                    failure.message?.takeIf { it.isNotBlank() } ?: "Engine startup failed"
                )
                false
            }
        }

    suspend fun reportInternetVerification(verified: Boolean) =
        lifecycleMutex.withLock {
            val current = mutableState.value
            if (current is TunnelState.TunnelActive) {
                mutableState.value = if (verified) {
                    TunnelState.InternetVerified(current.sessionId)
                } else {
                    TunnelState.NoInternet(current.sessionId)
                }
            } else if (current is TunnelState.InternetVerified && !verified) {
                mutableState.value = TunnelState.NoInternet(current.sessionId)
            } else if (current is TunnelState.NoInternet && verified) {
                mutableState.value = TunnelState.InternetVerified(current.sessionId)
            }
        }

    suspend fun disconnect(): Boolean = lifecycleMutex.withLock {
        if (mutableState.value is TunnelState.Disconnected) return@withLock true
        try {
            engine.stop()
            mutableState.value = TunnelState.Disconnected
            true
        } catch (failure: Exception) {
            mutableState.value = TunnelState.Error(
                failure.message?.takeIf { it.isNotBlank() } ?: "Engine shutdown failed"
            )
            false
        }
    }
}

sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object Starting : TunnelState
    data class TunnelActive(val sessionId: String) : TunnelState
    data class InternetVerified(val sessionId: String) : TunnelState
    data class NoInternet(val sessionId: String) : TunnelState
    data class Error(val reason: String) : TunnelState
}
