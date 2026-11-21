package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WireGuardConfigTest {
    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private val config = """
        [Interface]
        PrivateKey = $key
        Address = 10.66.0.2/32, fd00::2/128

        [Peer]
        PublicKey = $key
        PresharedKey = $key
        Endpoint = vpn.example.com:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun parsesSinglePeerWgQuickProfile() {
        val parsed = WireGuardConfigParser.parse(config)
        assertEquals(listOf("10.66.0.2/32", "fd00::2/128"), parsed.addresses)
        assertEquals("vpn.example.com", parsed.peerHost)
        assertEquals(51820, parsed.peerPort)
        assertEquals(listOf("0.0.0.0/0", "::/0"), parsed.allowedIps)
        assertEquals(25, parsed.persistentKeepalive)
    }

    @Test
    fun buildsModernSingBoxEndpointSchema() {
        val imported = ProfileImporter.parseWireGuardConfig(config)
        assertTrue(imported is ProfileParseResult.Success)
        val profile = (imported as ProfileParseResult.Success).profile
        val endpoint = WireGuardEndpointBuilder.build(profile)
        assertEquals("wireguard", endpoint.getString("type"))
        assertEquals("wireguard-endpoint", endpoint.getString("tag"))
        assertEquals("10.66.0.2/32", endpoint.getJSONArray("address").getString(0))
        val peer = endpoint.getJSONArray("peers").getJSONObject(0)
        assertEquals("vpn.example.com", peer.getString("address"))
        assertEquals(51820, peer.getInt("port"))
        assertEquals(25, peer.getInt("persistent_keepalive_interval"))
        assertTrue(peer.has("pre_shared_key"))
        assertFalse(endpoint.has("server"))
    }

    @Test
    fun rejectsHooksAndUnknownDirectivesInsteadOfDroppingThem() {
        val unsafe = config.replace(
            "Address = 10.66.0.2/32, fd00::2/128",
            "Address = 10.66.0.2/32, fd00::2/128\nPostUp = iptables -F"
        )
        assertTrue(runCatching { WireGuardConfigParser.parse(unsafe) }.isFailure)
    }

    @Test
    fun rejectsInvalidKeyAndEndpointPort() {
        val badKey = config.replace("PrivateKey = $key", "PrivateKey = not-a-key")
        val badPort = config.replace("vpn.example.com:51820", "vpn.example.com:70000")
        assertTrue(runCatching { WireGuardConfigParser.parse(badKey) }.isFailure)
        assertTrue(runCatching { WireGuardConfigParser.parse(badPort) }.isFailure)
    }

    @Test
    fun buildsTunnelConfigWithEndpointAsFinalRoute() {
        val imported = ProfileImporter.parseWireGuardConfig(config)
        assertTrue(imported is ProfileParseResult.Success)
        val profile = (imported as ProfileParseResult.Success).profile
        val root = SingBoxConfigBuilder.build(profile)
        assertEquals("wireguard-endpoint", root.getJSONObject("route").getString("final"))
        assertEquals("wireguard", root.getJSONArray("endpoints").getJSONObject(0).getString("type"))
        assertEquals(2, root.getJSONArray("outbounds").length())
        assertFalse(root.getJSONArray("outbounds").toString().contains("\"type\":\"wireguard\""))
        assertEquals("wireguard-endpoint", root.getJSONObject("dns").getJSONArray("servers")
            .getJSONObject(1).getString("detour"))
    }

    @Test
    fun rejectsMultiplePeerSectionsInsteadOfMergingThem() {
        val multiplePeers = config + "\n\n[Peer]\nPublicKey = $key\nEndpoint = other.example.com:51820\nAllowedIPs = 10.0.0.0/8"
        assertTrue(runCatching { WireGuardConfigParser.parse(multiplePeers) }.isFailure)
    }
}
