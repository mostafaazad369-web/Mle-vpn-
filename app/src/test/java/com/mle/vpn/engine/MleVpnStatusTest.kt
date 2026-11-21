package com.mle.vpn.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MleVpnStatusTest {
    @Before
    fun resetBefore() {
        MleVpnStatus.disconnected()
    }

    @After
    fun resetAfter() {
        MleVpnStatus.disconnected()
    }

    @Test
    fun trafficCountersArePublishedWithoutChangingConnectionPhase() {
        MleVpnStatus.starting()

        MleVpnStatus.updateTraffic(bytesOut = 12_345L, bytesIn = 67_890L)

        val status = MleVpnStatus.status.value
        assertEquals(MleConnectionPhase.STARTING, status.phase)
        assertEquals(12_345L, status.bytesOut)
        assertEquals(67_890L, status.bytesIn)
        assertTrue(status.trafficStatsAvailable)
    }

    @Test
    fun negativeNativeCountersAreClampedToZero() {
        MleVpnStatus.tunnelActive()

        MleVpnStatus.updateTraffic(bytesOut = -1L, bytesIn = -50L)

        val status = MleVpnStatus.status.value
        assertTrue(status.bytesOut >= 0L)
        assertTrue(status.bytesIn >= 0L)
        assertEquals(0L, status.bytesOut)
        assertEquals(0L, status.bytesIn)
        assertTrue(status.trafficStatsAvailable)
    }

    @Test
    fun connectionStateCanBeIndependentlyMarkedAsVerified() {
        MleVpnStatus.tunnelActive()
        MleVpnStatus.updateTraffic(bytesOut = 100L, bytesIn = 200L)
        MleVpnStatus.internetVerified()

        val status = MleVpnStatus.status.value
        assertEquals(MleConnectionPhase.INTERNET_VERIFIED, status.phase)
        assertEquals(100L, status.bytesOut)
        assertEquals(200L, status.bytesIn)
        assertTrue(status.trafficStatsAvailable)
        assertFalse(status.detail.isBlank())
    }
}
