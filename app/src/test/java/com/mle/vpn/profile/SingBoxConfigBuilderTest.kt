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
        val dnsServers = config.getJSONObject("dns").getJSONArray("servers")
        assertEquals("local", dnsServers.getJSONObject(0).getString("type"))
        assertEquals("bootstrap", dnsServers.getJSONObject(0).getString("tag"))
        assertEquals("https", dnsServers.getJSONObject(1).getString("type"))
        assertEquals("proxy", config.getJSONObject("route").getString("final"))
        assertTrue(config.getJSONObject("route").getBoolean("auto_detect_interface"))
        assertTrue(config.getJSONObject("route").getBoolean("override_android_vpn"))
        assertEquals("bootstrap", config.getJSONObject("route").getString("default_domain_resolver"))
        val outbounds = config.getJSONArray("outbounds")
        assertEquals("vless", outbounds.getJSONObject(0).getString("type"))
        assertEquals("bootstrap", outbounds.getJSONObject(0).getString("domain_resolver"))
        assertTrue(outbounds.length() >= 3)
    }
    @Test
    fun addsOnlyValidPackagesToTheTunIncludeList() {
        val profile = ImportedProfile(
            id = "app-filter",
            protocol = ProfileProtocol.VLESS,
            displayName = "App filter",
            host = "example.com",
            port = 443,
            sourceUri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&sni=example.com#test"
        )
        val config = SingBoxConfigBuilder.build(
            profile,
            listOf("com.example.browser", "invalid package", "com.example.chat")
        )
        val include = config.getJSONArray("inbounds").getJSONObject(0).getJSONArray("include_package")
        assertEquals(3, include.length())
        assertEquals("com.example.browser", include.getString(0))
        assertEquals("com.example.chat", include.getString(1))
        assertEquals("com.mle.vpn", include.getString(2))
    }

}
