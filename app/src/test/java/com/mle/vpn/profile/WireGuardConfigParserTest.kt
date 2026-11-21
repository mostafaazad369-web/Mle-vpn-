package com.mle.vpn.profile

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WireGuardConfigParserTest {
    private val key = Base64.encodeToString(ByteArray(32) { 7 }, Base64.NO_WRAP)
    private fun config(extra: String = "") = """
        [Interface]
        PrivateKey = $key
        Address = 10.7.0.2/32, fd00::2/128
        DNS = 1.1.1.1, 2606:4700:4700::1111
        MTU = 1380

        [Peer]
        PublicKey = $key
        Endpoint = vpn.example.net:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
        $extra
    """.trimIndent()

    @Test
    fun parsesInterfaceAndPeerAndBuildsEndpoint() {
        val parsed = WireGuardConfigParser.parse(config())
        assertEquals(2, parsed.addresses.size)
        assertEquals("vpn.example.net", parsed.peers.single().host)
        assertEquals(51820, parsed.peers.single().port)
        assertEquals(1380, parsed.mtu)
        assertEquals("wireguard", parsed.toSingBoxEndpoint().getString("type"))
        assertEquals(1, parsed.toSingBoxEndpoint().getJSONArray("peers").length())
    }

    @Test
    fun rejectsMissingPeerAndUnsafeUnknownOptions() {
        assertFalse(runCatching {
            WireGuardConfigParser.parse("[Interface]\nPrivateKey = $key\nAddress = 10.0.0.2/32")
        }.isSuccess)
        assertFalse(runCatching {
            WireGuardConfigParser.parse(config("PreUp = curl https://example.invalid"))
        }.isSuccess)
    }

    @Test
    fun rejectsInvalidKeysAndPorts() {
        assertFalse(runCatching {
            WireGuardConfigParser.parse(config().replace("PublicKey = $key", "PublicKey = invalid"))
        }.isSuccess)
        assertFalse(runCatching {
            WireGuardConfigParser.parse(config().replace("51820", "70000"))
        }.isSuccess)
    }

    @Test
    fun roundTripsStoredProfileAndBuildsFullTunnel() {
        val stored = WireGuardConfigParser.encodeForStorage(config())
        val profile = ProfileImporter.parse(stored) as ProfileParseResult.Success
        assertEquals(ProfileProtocol.WIREGUARD, profile.profile.protocol)
        val json = SingBoxConfigBuilder.build(profile.profile)
        assertEquals("wireguard", json.getJSONArray("endpoints").getJSONObject(0).getString("type"))
        assertEquals("wg-ep", json.getJSONObject("route").getString("final"))
        assertEquals("bootstrap", json.getJSONObject("route").getString("default_domain_resolver"))
        assertEquals("1.1.1.1", json.getJSONObject("dns").getJSONArray("servers").getJSONObject(1).getString("server"))
    }
}
