package com.mle.vpn.engine.vpngate

/**
 * Public VPN Gate relay metadata. The inline OpenVPN profile is retained only in memory
 * until the user explicitly imports a selected relay into the encrypted profile vault.
 */
data class VpnGateServer(
    val hostName: String,
    val ip: String,
    val score: Long,
    val pingMs: Int,
    val speedBytesPerSecond: Long,
    val country: String,
    val countryCode: String,
    val sessions: Int,
    val uptimeMs: Long,
    val openVpnConfig: String,
    val remoteHost: String,
    val remotePort: Int
) {
    val id: String get() = "vpngate:$hostName"
    val speedMbps: Double get() = speedBytesPerSecond * 8.0 / 1_000_000.0
    val isOfficialRelay: Boolean get() = hostName.startsWith("public-vpn-")
}
