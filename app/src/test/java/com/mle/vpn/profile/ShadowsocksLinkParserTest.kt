package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShadowsocksLinkParserTest {
    @Test
    fun parsesSip002Base64UserInfo() {
        val link = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNz@ss.example.com:8388#Fast%20Node"
        val parsed = ShadowsocksLinkParser.parse(link)
        assertEquals("chacha20-ietf-poly1305", parsed.method)
        assertEquals("pass", parsed.password)
        assertEquals("ss.example.com", parsed.host)
        assertEquals(8388, parsed.port)
        assertEquals("Fast Node", parsed.name)
    }

    @Test
    fun parsesPlainUserInfoAndBuildsSingBoxOutbound() {
        val link = "ss://aes-128-gcm:secret@ss.example.com:443#Office"
        val profile = (ProfileImporter.parse(link) as ProfileParseResult.Success).profile
        val json = SingBoxOutboundBuilder.build(profile)
        assertEquals("shadowsocks", json.getString("type"))
        assertEquals("aes-128-gcm", json.getString("method"))
        assertEquals("secret", json.getString("password"))
        assertEquals(443, json.getInt("server_port"))
    }

    @Test
    fun rejectsPluginLinksUntilPluginRuntimeExists() {
        val result = runCatching {
            ShadowsocksLinkParser.parse("ss://aes-128-gcm:secret@ss.example.com:443?plugin=obfs-local")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsInvalidPort() {
        assertTrue(runCatching {
            ShadowsocksLinkParser.parse("ss://aes-128-gcm:secret@ss.example.com:70000")
        }.isFailure)
    }
}
