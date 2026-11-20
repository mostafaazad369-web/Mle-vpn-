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
