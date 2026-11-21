package com.mle.vpn.engine.cloudflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudflareDoctorTest {
    @Test
    fun parsesTraceFieldsWithoutTreatingThemAsVpnProof() {
        val result = CloudflareDoctor.parse(
            "fl=12ab\nip=203.0.113.8\ncolo=FRA\nwarp=off\ngateway=off\n"
        )
        assertEquals("203.0.113.8", result.ip)
        assertEquals("FRA", result.colo)
        assertEquals("off", result.warp)
        assertEquals("off", result.gateway)
        assertEquals(200, result.httpStatus)
    }

    @Test
    fun absentOptionalFieldsRemainNull() {
        val result = CloudflareDoctor.parse("colo=FRA\n")
        assertEquals("FRA", result.colo)
        assertNull(result.ip)
        assertNull(result.warp)
    }

    @Test
    fun rejectsUnexpectedBodiesAndFailedHttpStatuses() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudflareDoctor.parse("not a Cloudflare trace")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudflareDoctor.parse("warp=off", 503)
        }
    }
}
