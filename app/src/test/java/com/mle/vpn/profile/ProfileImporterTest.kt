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

    @Test
    fun importsHysteria2FeedsUsedByPublicSourceCatalog() {
        val parsed = ProfileImporter.parse(
            "hysteria2://secret@example.com:443?sni=edge.example"
        )
        assertTrue(parsed is ProfileParseResult.Success)
        val profile = (parsed as ProfileParseResult.Success).profile
        assertEquals(ProfileProtocol.HYSTERIA2, profile.protocol)
        val outbound = SingBoxOutboundBuilder.build(profile)
        assertEquals("hysteria2", outbound.getString("type"))
        assertEquals("example.com", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("edge.example", outbound.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun importsWireGuardConfAsSingleProfile() {
        val key = android.util.Base64.encodeToString(ByteArray(32) { 1 }, android.util.Base64.NO_WRAP)
        val conf = listOf(
            "[Interface]",
            "PrivateKey = $key",
            "Address = 10.0.0.2/32",
            "[Peer]",
            "PublicKey = $key",
            "Endpoint = wg.example:51820",
            "AllowedIPs = 0.0.0.0/0"
        ).joinToString("\n")
        val batch = ProfileImporter.parseBatch(conf)
        assertEquals(1, batch.profiles.size)
        assertEquals(ProfileProtocol.WIREGUARD, batch.profiles.single().protocol)
    }

    @Test
    fun importsAnyTlsSocksAndHttpProfiles() {
        val cases = listOf(
            "anytls://secret@example.com:443?sni=front.example" to ProfileProtocol.ANYTLS,
            "socks://user:pass@example.com:1080" to ProfileProtocol.SOCKS,
            "http://user:pass@example.com:8080" to ProfileProtocol.HTTP
        )
        cases.forEach { (uri, expectedProtocol) ->
            val parsed = ProfileImporter.parse(uri)
            assertTrue("Expected successful parse for $expectedProtocol", parsed is ProfileParseResult.Success)
            val profile = (parsed as ProfileParseResult.Success).profile
            assertEquals(expectedProtocol, profile.protocol)
            assertEquals(expectedProtocol.name.lowercase(), SingBoxOutboundBuilder.build(profile).getString("type"))
        }
    }

}
