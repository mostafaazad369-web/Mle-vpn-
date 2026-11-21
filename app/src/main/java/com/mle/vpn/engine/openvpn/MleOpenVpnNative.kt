package com.mle.vpn.engine.openvpn

import androidx.annotation.Keep

/**
 * Stable MLE-owned JNI boundary for the OpenVPN 3 core.
 *
 * The native library remains optional on ABIs where it is not packaged. The
 * app only requests a service start for an explicitly selected and preflighted
 * OpenVPN profile after Android VPN consent has been granted.
 */
@Keep
object MleOpenVpnNative {
    @Volatile
    private var availability: Boolean? = null

    @Synchronized
    fun available(): Boolean {
        availability?.let { return it }
        val loaded = try {
            System.loadLibrary(LIBRARY_NAME)
            apiVersion() == API_VERSION
        } catch (_: LinkageError) {
            false
        } catch (_: Exception) {
            false
        }
        availability = loaded
        return loaded
    }

    external fun apiVersion(): Int
    external fun evaluate(config: String): String
    external fun create(): Long
    external fun run(
        handle: Long,
        config: String,
        username: String,
        password: String,
        callback: OpenVpnCallbacks
    ): String
    external fun stop(handle: Long)
    external fun release(handle: Long)

    private const val LIBRARY_NAME = "mleopenvpn"
    private const val API_VERSION = 1
}

/**
 * Callback ABI expected by the native bridge. Keep these method names,
 * argument order and JVM types synchronized with the C++ GetMethodID calls.
 */
@Keep
abstract class OpenVpnCallbacks {
    @Keep
    open fun configureTun(operation: String, value: Int): Boolean = false

    @Keep
    open fun addAddress(address: String, prefix: Int): Boolean = false

    @Keep
    open fun addRoute(address: String, prefix: Int): Boolean = false

    @Keep
    open fun addDns(address: String, ignored: Int): Boolean = false

    @Keep
    open fun addSearchDomain(domain: String, ignored: Int): Boolean = false

    @Keep
    open fun establishTun(): Int = -1

    @Keep
    open fun onCoreEvent(name: String, error: Boolean) = Unit

    @Keep
    open fun onCoreTraffic(bytesOut: Long, bytesIn: Long) = Unit
}
