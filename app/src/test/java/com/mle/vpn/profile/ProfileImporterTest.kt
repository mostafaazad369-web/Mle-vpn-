package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileImporterTest {
    @Test
    fun parsesVlessAndPreservesTransportParameters() {
        val result = ProfileImporter.parse(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&sni=edge.example&type=ws&path=%2Fgateway&host=cdn.example#node"
        )
        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.VLESS, profile.protocol)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        val outbound = SingBoxOutboundBuilder.build(profile)
        assertEquals("ws", outbound.getJSONObject("transport").getString("type"))
        assertEquals("/gateway", outbound.getJSONObject("transport").getString("path"))
        assertEquals("edge.example", outbound.getJSONObject("tls").getString("server_name"))
    }


    @Test
    fun importsValidatedOpenVpnProfilesWithoutPassingThemToSingBox() {
        val fingerprint = "SHA256:" + List(32) { "A0" }.joinToString(":")
        val ovpn = """
            client
            dev tun
            proto udp
            remote vpn.example.net 443 udp
            remote-cert-tls server
            peer-fingerprint $fingerprint
            auth-user-pass
        """.trimIndent()
        val result = ProfileImporter.parseOpenVpnConfig(ovpn)
        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.OPENVPN, profile.protocol)
        assertEquals("vpn.example.net", profile.host)
        assertEquals(443, profile.port)
        assertTrue(OpenVpnProfilePreflight.inspect(profile.sourceUri).requiresCredentials)
    }

    @Test
    fun rejectsUnsupportedTransportInsteadOfSilentlyDroppingIt() {
        val result = ProfileImporter.parse(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&type=kcp"
        )
        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        val conversion = runCatching { SingBoxOutboundBuilder.build(profile) }
        assertTrue(conversion.isFailure)
    }

    @Test
    fun batchImportRemovesDuplicatesAndCountsInvalidLines() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&sni=example.com"
        val batch = ProfileImporter.parseBatch(listOf(uri, uri, "not-a-profile").joinToString("\n"))
        assertEquals(1, batch.profiles.size)
        assertEquals(1, batch.duplicateCount)
        assertEquals(1, batch.rejectedCount)
    }
}
