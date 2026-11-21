package com.mle.vpn.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MleVpnEngineArbiterTest {
    @Before fun resetBefore() = MleVpnEngineArbiter.resetForTests()
    @After fun resetAfter() = MleVpnEngineArbiter.resetForTests()

    @Test
    fun refusesSecondEngineWhileTunnelIsOwned() {
        val singBox = MleVpnEngineArbiter.acquire(MleVpnEngine.SING_BOX)
        assertEquals(MleVpnEngine.SING_BOX, MleVpnEngineArbiter.activeEngine())
        try {
            MleVpnEngineArbiter.acquire(MleVpnEngine.OPENVPN)
            fail("OpenVPN must not take over a live sing-box tunnel")
        } catch (expected: IllegalStateException) {
            assertEquals(MleVpnEngine.SING_BOX, MleVpnEngineArbiter.activeEngine())
        }
        MleVpnEngineArbiter.release(singBox)
        assertNull(MleVpnEngineArbiter.activeEngine())
    }

    @Test
    fun sameEngineAcquireIsIdempotentForServiceReload() {
        val first = MleVpnEngineArbiter.acquire(MleVpnEngine.SING_BOX)
        val second = MleVpnEngineArbiter.acquire(MleVpnEngine.SING_BOX)
        assertEquals(first, second)
        assertEquals(MleVpnEngine.SING_BOX, MleVpnEngineArbiter.activeEngine())
    }

    @Test
    fun staleLeaseCannotReleaseNewEngineOwner() {
        val old = MleVpnEngineArbiter.acquire(MleVpnEngine.SING_BOX)
        MleVpnEngineArbiter.release(old)
        val current = MleVpnEngineArbiter.acquire(MleVpnEngine.OPENVPN)
        MleVpnEngineArbiter.release(old)
        assertEquals(MleVpnEngine.OPENVPN, MleVpnEngineArbiter.activeEngine())
        MleVpnEngineArbiter.release(current)
        assertNull(MleVpnEngineArbiter.activeEngine())
    }
}
