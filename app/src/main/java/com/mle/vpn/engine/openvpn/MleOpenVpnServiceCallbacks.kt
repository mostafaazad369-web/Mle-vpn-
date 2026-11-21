package com.mle.vpn.engine.openvpn

import com.mle.vpn.engine.MleVpnService

/**
 * Adapts OpenVPN 3's tun_builder callbacks to the single MLE VpnService.
 *
 * This callback object is intentionally not wired to a native run loop yet.
 * It is the service boundary that the JNI worker will receive after the
 * OPENVPN arbiter lease and cancellation lifecycle are implemented.
 */
class MleOpenVpnServiceCallbacks(
    private val service: MleVpnService,
    private val onEvent: (name: String, error: Boolean) -> Unit = { _, _ -> },
    private val onTraffic: (bytesOut: Long, bytesIn: Long) -> Unit = { _, _ -> }
) : OpenVpnCallbacks() {
    private val tun = OpenVpnTunConfig.Builder()

    @Synchronized
    override fun configureTun(operation: String, value: Int): Boolean = runCatching {
        when (operation) {
            "new" -> {
                tun.reset()
                true
            }
            "mtu" -> {
                tun.setMtu(value)
                true
            }
            "protect" -> service.protectTunnelSocket(value)
            else -> false
        }
    }.getOrDefault(false)

    @Synchronized
    override fun addAddress(address: String, prefix: Int): Boolean =
        runCatching { tun.addAddress(address, prefix); true }.getOrDefault(false)

    @Synchronized
    override fun addRoute(address: String, prefix: Int): Boolean =
        runCatching { tun.addRoute(address, prefix); true }.getOrDefault(false)

    @Synchronized
    override fun addDns(address: String, ignored: Int): Boolean =
        runCatching { tun.addDns(address); true }.getOrDefault(false)

    @Synchronized
    override fun addSearchDomain(domain: String, ignored: Int): Boolean =
        runCatching { tun.addSearchDomain(domain); true }.getOrDefault(false)

    @Synchronized
    override fun establishTun(): Int = runCatching {
        service.establishOpenVpnTun(tun.build())
    }.getOrDefault(-1)

    override fun onCoreEvent(name: String, error: Boolean) = onEvent(name, error)

    override fun onCoreTraffic(bytesOut: Long, bytesIn: Long) =
        onTraffic(bytesOut.coerceAtLeast(0), bytesIn.coerceAtLeast(0))
}
