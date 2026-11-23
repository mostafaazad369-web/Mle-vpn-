package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdvancedUdpOutboundTest {
    @Test
    fun hysteria2UriBuildsSingBoxOutbound() {
        val parsed = ProfileImporter.parse(
            "hysteria2://secret-pass@example.com:443?sni=cdn.example.com&alpn=h3&obfs=salamander&obfs-password=mask"
        )
        assertTrue("Hysteria2 URI should import", parsed is ProfileParseResult.Success)
        val profile = (parsed as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.HYSTERIA2, profile.protocol)
        val outbound = SingBoxOutboundBuilder.build(profile)
        assertEquals("hysteria2", outbound.getString("type"))
        assertEquals("example.com", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("secret-pass", outbound.getString("password"))
        assertEquals("cdn.example.com", outbound.getJSONObject("tls").getString("server_name"))
        assertEquals("salamander", outbound.getJSONObject("obfs").getString("type"))
        assertEquals("mask", outbound.getJSONObject("obfs").getString("password"))
    }

    @Test
    fun hysteria2RejectsObfsPasswordWithoutType() {
        val parsed = ProfileImporter.parse("hysteria2://secret@example.com:443?obfs-password=mask")
        assertTrue(parsed is ProfileParseResult.Success)
        val profile = (parsed as ProfileParseResult.Success).profile
        assertTrue(runCatching { SingBoxOutboundBuilder.build(profile) }.isFailure)
    }

    @Test
    fun tuicUriBuildsTlsProtectedOutboundAndDisablesZeroRtt() {
        val parsed = ProfileImporter.parse(
            "tuic://123e4567-e89b-12d3-a456-426614174000:secret@example.com:443?sni=cdn.example.com&congestion_control=bbr&udp_relay_mode=native"
        )
        assertTrue("TUIC URI should import", parsed is ProfileParseResult.Success)
        val profile = (parsed as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.TUIC, profile.protocol)
        val outbound = SingBoxOutboundBuilder.build(profile)
        assertEquals("tuic", outbound.getString("type"))
        assertEquals("123e4567-e89b-12d3-a456-426614174000", outbound.getString("uuid"))
        assertEquals("secret", outbound.getString("password"))
        assertEquals("bbr", outbound.getString("congestion_control"))
        assertFalse(outbound.getBoolean("zero_rtt_handshake"))
        assertEquals("cdn.example.com", outbound.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun tuicRejectsInvalidUuidAndUnknownCongestionAlgorithm() {
        val invalidUuid = ProfileImporter.parse("tuic://not-a-uuid:secret@example.com:443")
        assertTrue(invalidUuid is ProfileParseResult.Success)
        assertTrue(runCatching {
            SingBoxOutboundBuilder.build((invalidUuid as ProfileParseResult.Success).profile)
        }.isFailure)

        val invalidCongestion = ProfileImporter.parse(
            "tuic://123e4567-e89b-12d3-a456-426614174000:secret@example.com:443?congestion_control=unknown"
        )
        assertTrue(invalidCongestion is ProfileParseResult.Success)
        assertTrue(runCatching {
            SingBoxOutboundBuilder.build((invalidCongestion as ProfileParseResult.Success).profile)
        }.isFailure)
    }
    @Test
    fun hysteria2UriCannotDisableTlsCertificateVerification() {
        val parsed = ProfileImporter.parse(
            "hysteria2://secret@example.com:443?insecure=1"
        ) as ProfileParseResult.Success
        val tls = SingBoxOutboundBuilder.build(parsed.profile).getJSONObject("tls")
        assertFalse(tls.optBoolean("insecure", false))
    }

    @Test
    fun tuicUriCannotDisableTlsCertificateVerification() {
        val parsed = ProfileImporter.parse(
            "tuic://123e4567-e89b-12d3-a456-426614174000:secret@example.com:443?allowInsecure=1"
        ) as ProfileParseResult.Success
        val tls = SingBoxOutboundBuilder.build(parsed.profile).getJSONObject("tls")
        assertFalse(tls.optBoolean("insecure", false))
    }

}
