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
    fun importsBase64EncodedWireGuardConfig() {
        val encoded = Base64.encodeToString(config().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val batch = ProfileImporter.parseBatch(encoded)
        assertEquals(1, batch.profiles.size)
        assertEquals(ProfileProtocol.WIREGUARD, batch.profiles.single().protocol)
    }

    @Test
    fun identifiesCloudflareWarpPeerAndBuildsSingBoxEndpoint() {
        val warpConfig = config().replace(
            "PublicKey = $key",
            "PublicKey = " + ProfileImporter.WARP_PEER_PUBLIC_KEY
        )
        val batch = ProfileImporter.parseBatch(warpConfig)
        assertEquals(1, batch.profiles.size)
        val profile = batch.profiles.single()
        assertEquals(ProfileProtocol.WARP, profile.protocol)
        assertEquals("Cloudflare WARP • vpn.example.net", profile.displayName)
        val stored = WireGuardConfigParser.encodeForStorage(warpConfig)
        val restored = ProfileImporter.parse(stored) as ProfileParseResult.Success
        assertEquals(ProfileProtocol.WARP, restored.profile.protocol)
        val json = SingBoxConfigBuilder.build(profile)
        assertEquals("wireguard", json.getJSONArray("endpoints").getJSONObject(0).getString("type"))
        assertEquals("wg-ep", json.getJSONObject("route").getString("final"))
    }


    @Test
    fun parsesStandardWireGuardShareLinksAndNormalizesToEncryptedConfStorage() {
        val link = "wireguard://$key@vpn.example.net:51820?publickey=$key&address=10.7.0.2%2F32&dns=1.1.1.1&allowedips=0.0.0.0%2F0%2C%3A%3A%2F0&mtu=1380&keepalive=25#Test%20WG"
        val (config, name) = WireGuardConfigParser.parseUri(link)
        assertEquals("Test WG", name)
        assertEquals("vpn.example.net", config.peers.single().host)
        assertEquals("10.7.0.2/32", config.addresses.single())
        val parsed = ProfileImporter.parse(link) as ProfileParseResult.Success
        assertEquals(ProfileProtocol.WIREGUARD, parsed.profile.protocol)
        val endpoint = SingBoxConfigBuilder.build(parsed.profile).getJSONArray("endpoints").getJSONObject(0)
        assertEquals("wireguard", endpoint.getString("type"))
    }

    @Test
    fun parsesWARPShareLinkAndRejectsAmneziaOnlyQueryParameters() {
        val warpLink = "wg://$key@warp.example.net:2408?publickey=${ProfileImporter.WARP_PEER_PUBLIC_KEY}&address=172.16.0.2%2F32&reserved=1%2C2%2C3#WARP"
        val parsed = ProfileImporter.parse(warpLink) as ProfileParseResult.Success
        assertEquals(ProfileProtocol.WARP, parsed.profile.protocol)
        assertEquals("wireguard", SingBoxConfigBuilder.build(parsed.profile).getJSONArray("endpoints").getJSONObject(0).getString("type"))
        val awg = "wireguard://$key@vpn.example.net:51820?publickey=$key&address=10.7.0.2%2F32&jc=4"
        assertFalse(runCatching { WireGuardConfigParser.parseUri(awg) }.isSuccess)
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
    fun explicitlyRejectsAmneziaOptionsInsteadOfDowngradingToWireGuard() {
        assertFalse(runCatching {
            WireGuardConfigParser.parse(config().replace("MTU = 1380", "MTU = 1380\nJc = 4\nJmin = 40\nJmax = 70\nS1 = 0\nS2 = 0"))
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
