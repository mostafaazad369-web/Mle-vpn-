package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
class SingBoxConfigBuilderTest {
    @Test
    fun buildsTunDnsAndProxyRouteConfiguration() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&sni=example.com#test"
        val profile = ImportedProfile(
            id = "test-id",
            protocol = ProfileProtocol.VLESS,
            displayName = "Test",
            host = "example.com",
            port = 443,
            sourceUri = uri
        )
        val config = SingBoxConfigBuilder.build(profile)
        assertEquals("tun", config.getJSONArray("inbounds").getJSONObject(0).getString("type"))
        assertEquals("hijack", config.getJSONArray("inbounds").getJSONObject(0).getString("dns_mode"))
        assertEquals("https", config.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("type"))
        assertEquals("proxy", config.getJSONObject("route").getString("final"))
        assertTrue(config.getJSONObject("route").getBoolean("override_android_vpn"))
        val outbounds = config.getJSONArray("outbounds")
        assertEquals("vless", outbounds.getJSONObject(0).getString("type"))
        assertTrue(outbounds.length() >= 3)
    }
}
