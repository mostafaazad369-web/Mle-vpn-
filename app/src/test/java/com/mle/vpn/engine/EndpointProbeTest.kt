package com.mle.vpn.engine

import com.mle.vpn.profile.ImportedProfile
import com.mle.vpn.profile.ProfileProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
class EndpointProbeTest {
    private fun profile(host: String, port: Int) = ImportedProfile(
        id = "probe-test",
        protocol = ProfileProtocol.VLESS,
        displayName = "Endpoint probe test",
        host = host,
        port = port,
        sourceUri = "vless://test@example.invalid:443"
    )

    @Test
    fun rejectsLoopbackAndPrivateDestinationsBeforeConnecting() = runBlocking {
        val loopback = EndpointProbe.check(profile("127.0.0.1", 443))
        val privateNetwork = EndpointProbe.check(profile("192.168.1.1", 443))

        assertFalse(loopback.reachable)
        assertFalse(privateNetwork.reachable)
        assertTrue(loopback.message.contains("IP عمومی"))
    }

    @Test
    fun acceptsGloballyRoutableIpv4AndIpv6Literals() {
        assertTrue(EndpointProbe.isPubliclyRoutable(InetAddress.getByName("1.1.1.1")))
        assertTrue(EndpointProbe.isPubliclyRoutable(InetAddress.getByName("2606:4700:4700::1111")))
        assertTrue(EndpointProbe.isPubliclyRoutable(InetAddress.getByName("2001:4860:4860::8888")))
        assertTrue(EndpointProbe.isPubliclyRoutable(InetAddress.getByName("2001:200::1")))
    }

    @Test
    fun rejectsSpecialUseAndDocumentationRanges() {
        listOf(
            "0.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1",
            "172.16.0.1", "192.0.2.1", "192.168.1.1", "198.18.0.1",
            "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255",
            "::1", "fc00::1", "fe80::1", "2001:db8::1", "2001:2:1::1",
            "2001:1::1", "2001:100::1", "2001:1ff::1", "2001:2::1", "2002::1", "3fff::1"
        ).forEach { value ->
            assertFalse("$value must not be probed", EndpointProbe.isPubliclyRoutable(InetAddress.getByName(value)))
        }
    }

    @Test
    fun rejectsDocumentationAddressWithoutAttemptingTcp() = runBlocking {
        // TEST-NET addresses are intentionally rejected before a network attempt.
        val result = EndpointProbe.check(profile("203.0.113.10", 443))
        assertFalse(result.reachable)
        assertTrue(result.message.contains("IP عمومی"))
    }
}
