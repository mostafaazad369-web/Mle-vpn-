package com.mle.vpn.engine.openvpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnTunConfigTest {
    @Test
    fun collectsIpv4Ipv6RoutesDnsAndSearchDomains() {
        val builder = OpenVpnTunConfig.Builder()
        builder.setMtu(1420)
        builder.addAddress("10.8.0.2", 24)
        builder.addAddress("fd00::2", 64)
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)
        builder.addDns("1.1.1.1")
        builder.addDns("2606:4700:4700::1111")
        builder.addSearchDomain("Example.COM.")
        val config = builder.build()

        assertEquals(1420, config.mtu)
        assertEquals(2, config.addresses.size)
        assertEquals(2, config.routes.size)
        assertEquals(2, config.dnsServers.size)
        assertEquals(listOf("example.com"), config.searchDomains)
        assertTrue(config.addresses.any { it.ipv6 })
        assertFalse(config.addresses.first { !it.ipv6 }.ipv6)
    }

    @Test
    fun resetStartsANewServerPushedTunConfiguration() {
        val builder = OpenVpnTunConfig.Builder()
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.addDns("9.9.9.9")
        builder.reset()
        builder.addAddress("192.0.2.2", 32)
        builder.addRoute("192.0.2.0", 24)

        val config = builder.build()
        assertEquals(1500, config.mtu)
        assertEquals(listOf("192.0.2.2"), config.addresses.map { it.address })
        assertTrue(config.dnsServers.isEmpty())
    }

    @Test
    fun rejectsHostnamesMalformedPrefixesAndUnsafeMtu() {
        val builder = OpenVpnTunConfig.Builder()
        expectFailure { builder.addAddress("vpn.example.com", 24) }
        expectFailure { builder.addAddress("10.0.0.1", 33) }
        expectFailure { builder.setMtu(9001) }
        expectFailure { builder.addDns("dns.example.com") }
        expectFailure { builder.addSearchDomain("bad domain") }
    }

    @Test
    fun requiresAnInterfaceAddressAndRouteBeforeEstablishingTun() {
        val builder = OpenVpnTunConfig.Builder()
        expectFailure { builder.build() }
        builder.addAddress("10.8.0.2", 24)
        expectFailure { builder.build() }
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
