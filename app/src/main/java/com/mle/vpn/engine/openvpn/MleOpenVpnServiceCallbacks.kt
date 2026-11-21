package com.mle.vpn.engine.openvpn

import com.mle.vpn.engine.MleVpnService

/**
 * Adapts OpenVPN 3's tun_builder callbacks to the single MLE VpnService.
 *
 * The JNI worker receives this callback object after the OPENVPN arbiter
 * lease is acquired. It translates server-pushed TUN settings into the shared
 * MLE VpnService and forwards lifecycle and traffic events to the runtime.
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
        // Fail closed for IPv6: if the profile/core did not negotiate IPv6,
        // route it into the tunnel anyway so it cannot escape via the underlay.
        tun.addRoute("::", 0)
        service.establishOpenVpnTun(tun.build())
    }.getOrDefault(-1)

    override fun onCoreEvent(name: String, error: Boolean) = onEvent(name, error)

    override fun onCoreTraffic(bytesOut: Long, bytesIn: Long) =
        onTraffic(bytesOut.coerceAtLeast(0), bytesIn.coerceAtLeast(0))
}
