package com.mle.vpn.engine.openvpn

import android.util.Log
import com.mle.vpn.engine.MleVpnEngine
import com.mle.vpn.engine.MleVpnEngineArbiter
import com.mle.vpn.engine.MleVpnService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns one OpenVPN 3 client from creation through its blocking connect loop.
 *
 * The JNI run call is deliberately executed on a dedicated worker: OpenVPN's
 * connect() blocks for the lifetime of the session. The native handle and its
 * callback global reference are released only after run() has returned.
 */
internal class MleOpenVpnRuntime(
    private val service: MleVpnService,
    private val onConnected: () -> Unit,
    private val onFailure: (String) -> Unit,
    private val onFinished: () -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MLE-OpenVPN-Core").apply { isDaemon = true }
    }
    private val handle = AtomicLong(0L)
    private val stopping = AtomicBoolean(false)
    private val finished = CountDownLatch(1)
    private val started = AtomicBoolean(false)

    fun start(profile: String, username: String, password: String): Boolean {
        if (!started.compareAndSet(false, true)) return false
        if (!MleOpenVpnNative.available()) {
            started.set(false)
            return false
        }
        return try {
            executor.execute {
                var nativeHandle = 0L
                try {
                    if (stopping.get()) return@execute
                    nativeHandle = MleOpenVpnNative.create()
                    check(nativeHandle != 0L) { "OpenVPN native client could not be created." }
                    handle.set(nativeHandle)
                    if (stopping.get()) {
                        MleOpenVpnNative.stop(nativeHandle)
                        return@execute
                    }

                    val callbacks = MleOpenVpnServiceCallbacks(
                        service = service,
                        onEvent = { name, error ->
                            Log.i(TAG, "OpenVPN event: $name (error=$error)")
                            if (!error && name.contains("CONNECTED", ignoreCase = true)) {
                                onConnected()
                            } else if (error) {
                                onFailure("OpenVPN core reported an error: $name")
                                stop()
                            }
                        },
                        onTraffic = { bytesOut, bytesIn ->
                            Log.v(TAG, "OpenVPN traffic: out=$bytesOut in=$bytesIn")
                        }
                    )
                    val error = MleOpenVpnNative.run(
                        nativeHandle, profile, username, password, callbacks
                    )
                    if (error.isNotEmpty() && !stopping.get()) {
                        onFailure("OpenVPN startup failed: $error")
                    }
                } catch (error: Throwable) {
                    Log.e(TAG, "OpenVPN worker failed", error)
                    if (!stopping.get()) {
                        onFailure(error.message ?: "OpenVPN native worker failed.")
                    }
                } finally {
                    if (nativeHandle != 0L) {
                        // run() has returned, so its callback references and TUN
                        // use are finished. Only now is release() safe.
                        runCatching { MleOpenVpnNative.release(nativeHandle) }
                        handle.compareAndSet(nativeHandle, 0L)
                    }
                    finished.countDown()
                    runCatching { onFinished() }
                    executor.shutdown()
                }
            }
            true
        } catch (error: Throwable) {
            started.set(false)
            finished.countDown()
            runCatching { executor.shutdown() }
            onFailure(error.message ?: "OpenVPN worker could not be started.")
            false
        }
    }

    fun stop() {
        if (stopping.compareAndSet(false, true)) {
            val current = handle.get()
            if (current != 0L) runCatching { MleOpenVpnNative.stop(current) }
        }
    }

    fun stopAndAwait(timeoutMillis: Long = 8_000L): Boolean {
        stop()
        return try {
            finished.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private companion object {
        const val TAG = "MLE-OpenVPN"
    }
}
