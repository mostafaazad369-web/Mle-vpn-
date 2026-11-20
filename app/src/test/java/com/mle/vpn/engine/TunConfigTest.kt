package com.mle.vpn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunConfigTest {
    @Test
    fun acceptsDefaultConfiguration() {
        val config = TunConfig()
        assertEquals(1500, config.mtu)
        assertTrue(config.ipv6Enabled)
        assertFalse(config.dnsServers.isEmpty())
    }

    @Test
    fun acceptsIpv4OnlyConfiguration() {
        val config = TunConfig(ipv6Enabled = false, dnsServers = listOf("1.1.1.1"))
        assertFalse(config.ipv6Enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooSmallMtu() {
        TunConfig(mtu = 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyDnsList() {
        TunConfig(dnsServers = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCombinedApplicationAllowAndDenyLists() {
        TunConfig(
            allowedApplications = listOf("com.example.allowed"),
            disallowedApplications = listOf("com.example.denied")
        )
    }
}
