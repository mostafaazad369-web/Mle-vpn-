package com.mle.vpn.engine.vpngate

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnGateCsvParserTest {
    private fun row(host: String = "public-vpn-1", ip: String = "219.100.37.1",
                    profile: String = "client\nremote vpn.example.net 443\n") =
        listOf(host, ip, "1234", "18", "58000000", "Japan", "JP", "12",
            "1000000", "100", "200000", "0", "operator", "public", 
            Base64.getEncoder().encodeToString(profile.toByteArray())).joinToString(",")

    @Test fun parsesPublicRelayAndExtractsActualOpenVpnEndpoint() {
        val servers = VpnGateCsvParser.parse("*vpn_servers\n#header\n${row()}\n*")
        assertEquals(1, servers.size)
        val server = servers.single()
        assertEquals("vpn.example.net", server.remoteHost)
        assertEquals(443, server.remotePort)
        assertEquals("JP", server.countryCode)
        assertTrue(server.isOfficialRelay)
        assertEquals(464.0, server.speedMbps, 0.001)
    }

    @Test fun skipsMalformedRowsAndDeduplicatesByHostName() {
        val servers = VpnGateCsvParser.parse(
            row() + "\n" + row(ip = "999.1.1.1") + "\n" +
                row(host = "public-vpn-2", profile = "client\n")
        )
        assertEquals(1, servers.size)
    }

    @Test fun neverReturnsRowsWithoutAnInlineProfileEndpoint() {
        assertTrue(VpnGateCsvParser.parse(row(profile = "client\n")).isEmpty())
        assertFalse(VpnGateCsvParser.parse(row()).isEmpty())
    }
}
