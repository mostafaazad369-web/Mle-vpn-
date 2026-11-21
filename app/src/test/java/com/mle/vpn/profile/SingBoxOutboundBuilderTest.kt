package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SingBoxOutboundBuilderTest {
    private fun profile(protocol: ProfileProtocol, uri: String) = ImportedProfile(
        id = "test-id",
        protocol = protocol,
        displayName = "Test node",
        host = "example.com",
        port = 443,
        sourceUri = uri
    )


    @Test
    fun importsStandardWireGuardConfigAndBuildsEndpoint() {
        val config = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 10.10.0.2/32, fd00::2/128
            DNS = 1.1.1.1

            [Peer]
            PublicKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
            Endpoint = wg.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
            Reserved = 1, 2, 3
        """.trimIndent()
        val profile = ProfileImporter.parseBatch(config).profiles.single()
        assertEquals(ProfileProtocol.WIREGUARD, profile.protocol)
        val json = SingBoxConfigBuilder.build(profile)
        val endpoint = json.getJSONArray("endpoints").getJSONObject(0)
        assertEquals("wireguard", endpoint.getString("type"))
        assertEquals("wg.example.com", endpoint.getJSONArray("peers").getJSONObject(0).getString("address"))
        assertEquals(3, endpoint.getJSONArray("peers").getJSONObject(0).getJSONArray("reserved").length())
    }

    @Test
    fun rejectsAmneziaObfuscationUntilNativeRuntimeExists() {
        val config = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 10.10.0.2/32
            Jc = 4

            [Peer]
            PublicKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
            Endpoint = wg.example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        assertTrue(ProfileImporter.parseBatch(config).profiles.isEmpty())
    }

    @Test
    fun buildsVlessRealityOutbound() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=reality&type=tcp&sni=www.example.com&pbk=publicKey&sid=abcd&fp=chrome#test"
        val json = SingBoxOutboundBuilder.build(profile(ProfileProtocol.VLESS, uri))
        assertEquals("vless", json.getString("type"))
        assertEquals("example.com", json.getString("server"))
        assertEquals("publicKey", json.getJSONObject("tls").getJSONObject("reality").getString("public_key"))
        assertTrue(json.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test
    fun buildsTrojanWithWebSocketTransport() {
        val uri = "trojan://secret@example.com:443?type=ws&path=%2Fgateway&host=cdn.example.com#test"
        val json = SingBoxOutboundBuilder.build(profile(ProfileProtocol.TROJAN, uri))
        assertEquals("trojan", json.getString("type"))
        assertEquals("secret", json.getString("password"))
        assertEquals("ws", json.getJSONObject("transport").getString("type"))
        assertEquals("/gateway", json.getJSONObject("transport").getString("path"))
    }

    @Test
    fun rejectsUnsupportedVlessTransport() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&type=kcp"
        val result = runCatching {
            SingBoxOutboundBuilder.build(profile(ProfileProtocol.VLESS, uri))
        }
        assertFalse(result.isSuccess)
    }

    @Test
    fun neverDisablesTlsCertificateVerificationFromUriParameter() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&allowInsecure=1"
        val json = SingBoxOutboundBuilder.build(profile(ProfileProtocol.VLESS, uri))
        assertFalse(json.getJSONObject("tls").optBoolean("insecure", false))
    }
}
