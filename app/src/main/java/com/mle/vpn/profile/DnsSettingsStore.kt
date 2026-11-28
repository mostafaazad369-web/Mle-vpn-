package com.mle.vpn.profile

import android.content.Context

/** Stores non-secret DNS endpoint preferences; no credentials or private keys are stored here. */
class DnsSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("mle_dns_settings_v1", Context.MODE_PRIVATE)

    fun load(): DnsSettings {
        val defaults = DnsSettings.default()
        val provider = runCatching {
            DnsProvider.valueOf(preferences.getString(KEY_PROVIDER, null) ?: defaults.provider.name)
        }.getOrDefault(defaults.provider)
        val protocol = runCatching {
            DnsProtocol.valueOf(preferences.getString(KEY_PROTOCOL, null) ?: defaults.protocol.name)
        }.getOrDefault(defaults.protocol)
        val settings = DnsSettings(
            provider = provider,
            protocol = protocol,
            serverAddress = preferences.getString(KEY_SERVER, null)
                ?: DnsSettings.forProvider(provider, protocol).serverAddress,
            tlsServerName = preferences.getString(KEY_TLS_NAME, null)
                ?: DnsSettings.forProvider(provider, protocol).tlsServerName,
            port = preferences.getInt(KEY_PORT, if (protocol == DnsProtocol.DOH) 443 else 853),
            dohPath = preferences.getString(KEY_PATH, "/dns-query") ?: "/dns-query"
        )
        return if (runCatching { settings.validate() }.isSuccess) settings else defaults
    }

    fun save(settings: DnsSettings) {
        settings.validate()
        preferences.edit()
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_PROTOCOL, settings.protocol.name)
            .putString(KEY_SERVER, settings.serverAddress)
            .putString(KEY_TLS_NAME, settings.tlsServerName)
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_PATH, settings.dohPath)
            .apply()
    }

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_PROTOCOL = "protocol"
        const val KEY_SERVER = "server"
        const val KEY_TLS_NAME = "tls_server_name"
        const val KEY_PORT = "port"
        const val KEY_PATH = "doh_path"
    }
}
