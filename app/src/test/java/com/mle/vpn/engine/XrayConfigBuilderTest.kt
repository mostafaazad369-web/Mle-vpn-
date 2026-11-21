package com.mle.vpn.engine

import com.mle.vpn.profile.ProfileImporter
import com.mle.vpn.profile.ProfileParseResult
import com.mle.vpn.profile.ProfileProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XrayConfigBuilderTest {
    private fun profile(uri: String) =
        (ProfileImporter.parse(uri) as ProfileParseResult.Success).profile

    @Test
    fun buildsVlessRealityTunConfig() {
        val item = profile(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
                "?security=reality&type=tcp&sni=front.example&fp=chrome" +
                "&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=abcd"
        )
        val config = XrayConfigBuilder.build(item)
        assertEquals("tun", config.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
        assertEquals("mle-tun", config.getJSONArray("inbounds").getJSONObject(0).getString("tag"))
        assertEquals("vless", config.getJSONArray("outbounds").getJSONObject(0).getString("protocol"))
        val stream = config.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("reality", stream.getString("security"))
        assertEquals("front.example", stream.getJSONObject("realitySettings").getString("serverName"))
        assertEquals("mle-tun", config.getJSONObject("routing").getJSONArray("rules")
            .getJSONObject(0).getJSONArray("inboundTag").getString(0))
    }

    @Test
    fun buildsVmessWebSocketTransport() {
        val encoded = android.util.Base64.encodeToString(
            """{"v":"2","ps":"test","add":"example.com","port":"443","id":"11111111-2222-3333-4444-555555555555","aid":"0","scy":"auto","net":"ws","type":"none","host":"cdn.example.com","path":"/edge","tls":"tls","sni":"front.example"}""".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val item = profile("vmess://$encoded")
        val config = XrayConfigBuilder.build(item)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vmess", outbound.getString("protocol"))
        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("ws", stream.getString("network"))
        assertEquals("/edge", stream.getJSONObject("wsSettings").getString("path"))
        assertEquals("front.example", stream.getJSONObject("tlsSettings").getString("serverName"))
    }

    @Test
    fun buildsShadowsocksOutbound() {
        val item = profile("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@example.com:8388#test")
        val config = XrayConfigBuilder.build(item)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("shadowsocks", outbound.getString("protocol"))
        val server = outbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("example.com", server.getString("address"))
        assertEquals(8388, server.getInt("port"))
        assertEquals("aes-256-gcm", server.getString("method"))
        assertEquals("password", server.getString("password"))
    }

    @Test
    fun rejectsProtocolsWithoutAnXrayMapping() {
        val item = profile("hysteria2://secret@example.com:443?sni=front.example")
        assertEquals(ProfileProtocol.HYSTERIA2, item.protocol)
        assertTrue(runCatching { XrayConfigBuilder.build(item) }.isFailure)
    }
}
