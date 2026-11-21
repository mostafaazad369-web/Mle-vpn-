package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenVpnConfigParserTest {
    private val sample = """
        client
        dev tun
        proto udp
        remote vpn.example.com 1194
        remote backup.example.com 443 tcp-client
        remote-random
        nobind
        persist-key
        persist-tun
        remote-cert-tls server
        auth SHA256
        cipher AES-256-GCM
        data-ciphers AES-256-GCM:AES-128-GCM
        tls-version-min 1.2
        key-direction 1
        <ca>
        -----BEGIN CERTIFICATE-----
        ZmFrZS1jZXJ0
        -----END CERTIFICATE-----
        </ca>
        <tls-auth>
        -----BEGIN OpenVPN Static key V1-----
        ZmFrZS1rZXk=
        -----END OpenVPN Static key V1-----
        </tls-auth>
    """.trimIndent()

    @Test
    fun parsesInlineTlsProfileAndBuildsSingBoxEndpoint() {
        val parsed = OpenVpnConfigParser.parse(sample)
        assertEquals(2, parsed.remotes.size)
        assertEquals("tcp", parsed.remotes[1].network)
        val endpoint = parsed.toSingBoxEndpoint()
        assertEquals("openvpn-client", endpoint.getString("type"))
        assertEquals(2, endpoint.getJSONArray("servers").length())
        assertEquals("tls_auth", endpoint.getJSONObject("tls").getJSONObject("control_wrap").getString("type"))
        assertEquals("client", endpoint.getJSONObject("tls").getJSONObject("control_wrap").getString("direction"))
        assertEquals(2, endpoint.getJSONArray("data_ciphers").length())
    }

    @Test
    fun importsAndBuildsOpenVpnProfileThroughSingBoxConfig() {
        val profile = ProfileImporter.parseBatch(sample).profiles.single()
        assertEquals(ProfileProtocol.OPENVPN, profile.protocol)
        val config = SingBoxConfigBuilder.build(profile)
        assertEquals("openvpn-client", config.getJSONArray("endpoints").getJSONObject(0).getString("type"))
        assertEquals("ovpn-ep", config.getJSONObject("route").getString("final"))
        assertEquals("tun", config.getJSONArray("inbounds").getJSONObject(0).getString("type"))
    }

    @Test
    fun rejectsCompressionAndExternalPasswordFiles() {
        val compressed = sample.replace("client", "client\ncomp-lzo")
        val credentials = sample.replace("client", "client\nauth-user-pass credentials.txt")
        assertTrue(runCatching { OpenVpnConfigParser.parse(compressed) }.isFailure)
        assertTrue(runCatching { OpenVpnConfigParser.parse(credentials) }.isFailure)
    }

    @Test
    fun storedProfileKeepsPrivateMaterialEncoded() {
        val encoded = OpenVpnConfigParser.encodeForStorage(sample)
        assertTrue(encoded.startsWith("openvpn-conf://"))
        assertFalse(encoded.contains("BEGIN CERTIFICATE"))
        assertEquals(2, OpenVpnConfigParser.decodeStored(encoded).remotes.size)
    }
}
