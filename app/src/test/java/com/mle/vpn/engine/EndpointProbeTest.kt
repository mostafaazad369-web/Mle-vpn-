package com.mle.vpn.engine

import com.mle.vpn.profile.ImportedProfile
import com.mle.vpn.profile.ProfileProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
class EndpointProbeTest {
    private fun profile(host: String, port: Int) = ImportedProfile(
        id = "probe-test",
        protocol = ProfileProtocol.VLESS,
        displayName = "Local test",
        host = host,
        port = port,
        sourceUri = "vless://test@example.invalid:443"
    )

    @Test
    fun reportsReachableLocalTcpPort() = runBlocking {
        ServerSocket(0).use { server ->
            val result = EndpointProbe.check(profile("127.0.0.1", server.localPort))
            org.junit.Assert.assertTrue(result.reachable)
            assertNotNull(result.latencyMs)
        }
    }

    @Test
    fun reportsUnreachableClosedLocalPort() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val result = EndpointProbe.check(profile("127.0.0.1", port))
        assertFalse(result.reachable)
    }
}
