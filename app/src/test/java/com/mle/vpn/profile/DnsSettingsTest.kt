package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsSettingsTest {
    @Test
    fun providerPresetsAreValidForBothEncryptedProtocols() {
        DnsProvider.values().filter { it != DnsProvider.CUSTOM }.forEach { provider ->
            DnsProtocol.values().forEach { protocol ->
                DnsSettings.forProvider(provider, protocol).validate()
            }
        }
        assertEquals(443, DnsSettings.forProvider(DnsProvider.GOOGLE, DnsProtocol.DOH).port)
        assertEquals(853, DnsSettings.forProvider(DnsProvider.GOOGLE, DnsProtocol.DOT).port)
    }

    @Test
    fun acceptsNumericIpv4AndIpv6Endpoints() {
        DnsSettings(DnsProvider.CUSTOM, DnsProtocol.DOH, "9.9.9.9", "dns.example.net", 443).validate()
        DnsSettings(DnsProvider.CUSTOM, DnsProtocol.DOT, "2001:4860:4860::8888", "dns.example.net", 853).validate()
    }

    @Test
    fun rejectsHostnameEndpointAndInvalidPort() {
        val hostname = DnsSettings(DnsProvider.CUSTOM, DnsProtocol.DOH, "dns.example.net", "dns.example.net", 443)
        assertTrue(runCatching { hostname.validate() }.isFailure)

        val badPort = DnsSettings(DnsProvider.CUSTOM, DnsProtocol.DOT, "9.9.9.9", "dns.example.net", 70000)
        assertTrue(runCatching { badPort.validate() }.isFailure)
    }

    @Test
    fun rejectsInvalidTlsNameAndDoHPath() {
        val badTls = DnsSettings(DnsProvider.CUSTOM, DnsProtocol.DOT, "9.9.9.9", "bad host", 853)
        assertTrue(runCatching { badTls.validate() }.isFailure)

        val badPath = DnsSettings(DnsProvider.CUSTOM, DnsProtocol.DOH, "9.9.9.9", "dns.example.net", 443, "dns-query")
        assertTrue(runCatching { badPath.validate() }.isFailure)
    }
}
