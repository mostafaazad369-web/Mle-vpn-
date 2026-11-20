package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.util.Base64

@RunWith(RobolectricTestRunner::class)
class ProfileImporterTest {
    @Test
    fun parsesVlessWebSocketTlsLink() {
        val result = ProfileImporter.parse(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&type=ws&path=%2Fedge#NL"
        )
        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.VLESS, profile.protocol)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("NL", profile.displayName)
    }

    @Test
    fun parsesTrojanLink() {
        val result = ProfileImporter.parse("trojan://secret@example.net:8443?sni=example.net#Node")
        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.TROJAN, profile.protocol)
        assertEquals(8443, profile.port)
    }

    @Test
    fun rejectsInvalidPort() {
        val result = ProfileImporter.parse("vless://id@example.com:70000#bad")
        assertTrue(result is ProfileParseResult.Failure)
    }

    @Test
    fun batchImportDeduplicatesAndCountsRejectedLines() {
        val valid = "vless://11111111-2222-3333-4444-555555555555@example.com:443#A"
        val batch = ProfileImporter.parseBatch("$valid\n$valid\nnot-a-profile")
        assertEquals(1, batch.profiles.size)
        assertEquals(1, batch.duplicateCount)
        assertEquals(1, batch.rejectedCount)
    }

    @Test
    fun importsBase64Subscription() {
        val valid = "trojan://secret@example.net:443#A\nvless://id@example.com:443#B"
        val encoded = Base64.encodeToString(valid.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val batch = ProfileImporter.parseBatch(encoded)
        assertEquals(2, batch.profiles.size)
        assertEquals(0, batch.rejectedCount)
    }
}
