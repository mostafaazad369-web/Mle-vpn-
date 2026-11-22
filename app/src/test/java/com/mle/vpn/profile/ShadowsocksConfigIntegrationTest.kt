package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShadowsocksConfigIntegrationTest {
    @Test
    fun importsLegacyBase64LinkAndBuildsCompleteSingBoxConfig() {
        // Base64("aes-128-gcm:secret@ss.example.com:8388")
        val link = "ss://YWVzLTEyOC1nY206c2VjcmV0QHNzLmV4YW1wbGUuY29tOjgzODg#Legacy"
        val parsed = ProfileImporter.parse(link)
        assertTrue("Legacy SS URI should import", parsed is ProfileParseResult.Success)
        val profile = (parsed as ProfileParseResult.Success).profile
        val config = SingBoxConfigBuilder.build(profile)

        val outbounds = config.getJSONArray("outbounds")
        val proxy = outbounds.getJSONObject(0)
        assertEquals("shadowsocks", proxy.getString("type"))
        assertEquals("ss.example.com", proxy.getString("server"))
        assertEquals(8388, proxy.getInt("server_port"))
        assertEquals("aes-128-gcm", proxy.getString("method"))
        assertEquals("secret", proxy.getString("password"))
        assertEquals("proxy", config.getJSONObject("route").getString("final"))
        assertEquals("secure-dns", config.getJSONObject("dns").getString("final"))
    }

    @Test
    fun sip002LinkSurvivesBatchImportAndDuplicateRemoval() {
        val link = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNz@ss.example.com:8388#Fast"
        val batch = ProfileImporter.parseBatch("$link\n$link")
        assertEquals(1, batch.profiles.size)
        assertEquals(1, batch.duplicateCount)
        assertEquals(0, batch.rejectedCount)
        assertEquals(ProfileProtocol.SHADOWSOCKS, batch.profiles.single().protocol)
    }

    @Test
    fun pluginOptionsAreNotSilentlyDropped() {
        val result = ProfileImporter.parse(
            "ss://aes-128-gcm:secret@ss.example.com:443?plugin=obfs-local"
        )
        assertTrue(result is ProfileParseResult.Failure)
    }
}
