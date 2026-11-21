package com.mle.vpn.profile

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AmneziaWgConfigParserTest {
    private val key = Base64.encodeToString(ByteArray(32) { 9 }, Base64.NO_WRAP)

    private fun config() = """
        [Interface]
        PrivateKey = $key
        Address = 10.7.0.2/32
        DNS = 1.1.1.1
        Jc = 4
        Jmin = 40
        Jmax = 70
        S1 = 0
        S2 = 0
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4

        [Peer]
        PublicKey = $key
        Endpoint = awg.example.net:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun officialParserAcceptsObfuscationAndExtractsPeerEndpoint() {
        val parsed = AmneziaWgConfigParser.parse(config())
        assertEquals("awg.example.net", parsed.host)
        assertEquals(51820, parsed.port)
        assertTrue(AmneziaWgConfigParser.isAmneziaConfig(config()))
    }

    @Test
    fun importerStoresAndRestoresAmneziaConfigWithoutDowngradingToWireGuard() {
        val parsed = ProfileImporter.parse(config()) as ProfileParseResult.Success
        assertEquals(ProfileProtocol.AMNEZIAWG, parsed.profile.protocol)
        assertEquals("awg.example.net", parsed.profile.host)
        val restored = AmneziaWgConfigParser.decodeStored(parsed.profile.sourceUri)
        assertEquals(config(), restored)
        val batch = ProfileImporter.parseBatch(config())
        assertEquals(1, batch.profiles.size)
        assertEquals(ProfileProtocol.AMNEZIAWG, batch.profiles.single().protocol)
    }

    @Test
    fun storedAmneziaProfileIsRecognizedAfterVaultRoundTrip() {
        val imported = ProfileImporter.parse(config()) as ProfileParseResult.Success
        val reloaded = ProfileImporter.parse(imported.profile.sourceUri) as ProfileParseResult.Success
        assertEquals(ProfileProtocol.AMNEZIAWG, reloaded.profile.protocol)
        assertEquals(imported.profile.host, reloaded.profile.host)
    }
}
