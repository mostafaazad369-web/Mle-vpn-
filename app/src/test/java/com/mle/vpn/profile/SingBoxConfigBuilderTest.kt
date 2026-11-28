package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SingBoxConfigBuilderTest {
    private fun profile() = ImportedProfile(
        id = "test-id",
        protocol = ProfileProtocol.VLESS,
        displayName = "Test",
        host = "example.com",
        port = 443,
        sourceUri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&sni=example.com#test"
    )

    @Test
    fun buildsStrictDohConfigurationWithoutSystemDnsFallback() {
        val config = SingBoxConfigBuilder.build(profile())
        val tun = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", tun.getString("type"))
        assertEquals("hijack", tun.getString("dns_mode"))
        val outbounds = config.getJSONArray("outbounds")
        assertEquals("vless", outbounds.getJSONObject(0).getString("type"))
        assertTrue(outbounds.length() >= 3)
        val dns = config.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")

        assertEquals(2, servers.length())
        assertEquals("https", servers.getJSONObject(0).getString("type"))
        assertEquals("bootstrap", servers.getJSONObject(0).getString("tag"))
        assertEquals("direct", servers.getJSONObject(0).getString("detour"))
        assertEquals("1.1.1.1", servers.getJSONObject(0).getString("server"))
        assertEquals("cloudflare-dns.com", servers.getJSONObject(0).getJSONObject("tls").getString("server_name"))
        assertEquals("secure-dns", dns.getString("final"))
        assertEquals("https", servers.getJSONObject(1).getString("type"))
        assertEquals("proxy", servers.getJSONObject(1).getString("detour"))
        assertEquals("/dns-query", servers.getJSONObject(1).getString("path"))
        assertFalse((0 until servers.length()).any { servers.getJSONObject(it).optString("type") == "local" })

        val route = config.getJSONObject("route")
        assertEquals("bootstrap", route.getString("default_domain_resolver"))
        assertEquals("proxy", route.getString("final"))
        assertTrue(route.getBoolean("auto_detect_interface"))
        assertTrue(route.getBoolean("override_android_vpn"))
        assertEquals("bootstrap", config.getJSONArray("outbounds").getJSONObject(0).getString("domain_resolver"))
    }

    @Test
    fun buildsDotConfigurationWithCustomEndpoint() {
        val settings = DnsSettings(
            provider = DnsProvider.CUSTOM,
            protocol = DnsProtocol.DOT,
            serverAddress = "2001:4860:4860::8888",
            tlsServerName = "dns.example.net",
            port = 853
        )
        val config = SingBoxConfigBuilder.build(profile(), settings)
        val servers = config.getJSONObject("dns").getJSONArray("servers")

        assertEquals("tls", servers.getJSONObject(0).getString("type"))
        assertEquals("2001:4860:4860::8888", servers.getJSONObject(0).getString("server"))
        assertEquals("dns.example.net", servers.getJSONObject(0).getJSONObject("tls").getString("server_name"))
        assertEquals("tls", servers.getJSONObject(1).getString("type"))
        assertFalse(servers.getJSONObject(0).has("path"))
    }

    @Test
    fun rejectsHostnameAsDnsBootstrapAddress() {
        val settings = DnsSettings(
            provider = DnsProvider.CUSTOM,
            protocol = DnsProtocol.DOH,
            serverAddress = "resolver.example.net",
            tlsServerName = "resolver.example.net",
            port = 443
        )
        val error = runCatching { SingBoxConfigBuilder.build(profile(), settings) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
