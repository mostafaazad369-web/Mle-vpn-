package com.mle.vpn.profile

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DnsSettingsStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("mle_dns_settings_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistsAndRestoresCustomDotSettings() {
        val expected = DnsSettings(
            provider = DnsProvider.CUSTOM,
            protocol = DnsProtocol.DOT,
            serverAddress = "2001:4860:4860::8888",
            tlsServerName = "dns.example.net",
            port = 853
        )

        DnsSettingsStore(context).save(expected)

        assertEquals(expected, DnsSettingsStore(context).load())
    }

    @Test
    fun invalidPersistedEndpointFallsBackToSecureDefault() {
        context.getSharedPreferences("mle_dns_settings_v1", Context.MODE_PRIVATE)
            .edit()
            .putString("provider", DnsProvider.CUSTOM.name)
            .putString("protocol", DnsProtocol.DOH.name)
            .putString("server", "resolver.example.net")
            .putString("tls_server_name", "resolver.example.net")
            .putInt("port", 443)
            .putString("doh_path", "/dns-query")
            .commit()

        assertEquals(DnsSettings.default(), DnsSettingsStore(context).load())
    }
}
