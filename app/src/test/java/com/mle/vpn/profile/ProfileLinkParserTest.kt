package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileLinkParserTest {
    @Test fun parsesVlessEndpoint() {
        val profile = ProfileLinkParser.parse("vless://id@example.com:443#edge")
        assertEquals(ProxyProtocol.VLESS, profile?.protocol)
        assertEquals("example.com", profile?.server)
        assertEquals(443, profile?.port)
        assertEquals("edge", profile?.name)
    }

    @Test fun rejectsUnknownAndMalformedLinks() {
        assertNull(ProfileLinkParser.parse("ftp://example.com:21"))
        assertNull(ProfileLinkParser.parse("vless://missing-port"))
    }
}
