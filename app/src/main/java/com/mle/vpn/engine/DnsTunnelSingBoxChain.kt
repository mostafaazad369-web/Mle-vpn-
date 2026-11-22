package com.mle.vpn.engine

/**
 * Generic DNS-tunnel -> sing-box chain contract.
 *
 * MLE implements its own adapter using independently licensed protocol
 * implementations. It does not embed, copy, or redistribute third-party VPN
 * apps whose licenses prohibit that use.
 */
data class LocalSocksEndpoint(
    val host: String = "127.0.0.1",
    val port: Int,
    val username: String? = null,
    val password: String? = null
) {
    init {
        require(host == "127.0.0.1" || host == "::1") { "Bridge must bind to loopback only." }
        require(port in 1024..65535) { "Invalid local SOCKS port." }
    }
}

enum class ChainStage {
    IDLE, STARTING_DNS_TUNNEL, WAITING_FOR_LOCAL_PROXY, TESTING_UPSTREAM,
    STARTING_SING_BOX, TESTING_FULL_TUNNEL, CONNECTED, STOPPING, FAILED
}

data class ChainHealth(
    val tunnelProcessAlive: Boolean,
    val socksListenerReady: Boolean,
    val upstreamReachable: Boolean,
    val singBoxTunEstablished: Boolean,
    val endToEndRequestSucceeded: Boolean,
    val dnsLeakCheckPassed: Boolean
) {
    val isHealthy: Boolean
        get() = tunnelProcessAlive && socksListenerReady && upstreamReachable &&
            singBoxTunEstablished && endToEndRequestSucceeded && dnsLeakCheckPassed
}

interface DnsTunnelRuntime {
    suspend fun start(profileUri: String, endpoint: LocalSocksEndpoint): EngineResult
    suspend fun awaitSocksReady(endpoint: LocalSocksEndpoint, timeoutMs: Long): Boolean
    suspend fun testEgress(endpoint: LocalSocksEndpoint): ConnectivityReport
    suspend fun stop(): EngineResult
}

interface SingBoxRuntime {
    suspend fun startTunThroughSocks(endpoint: LocalSocksEndpoint): EngineResult
    suspend fun testFullTunnel(): ConnectivityReport
    suspend fun stopTun(): EngineResult
}

/** Starts the upstream DNS tunnel first and sing-box as the sole Android TUN owner. */
class DnsTunnelSingBoxChain(
    private val dnsTunnel: DnsTunnelRuntime,
    private val singBox: SingBoxRuntime
) {
    var stage: ChainStage = ChainStage.IDLE
        private set

    suspend fun connect(
        profileUri: String,
        endpoint: LocalSocksEndpoint,
        onHealth: suspend (ChainHealth) -> Unit
    ): EngineResult {
        if (stage != ChainStage.IDLE && stage != ChainStage.FAILED) {
            return EngineResult(false, "A chain session is already active.")
        }
        try {
            stage = ChainStage.STARTING_DNS_TUNNEL
            val start = dnsTunnel.start(profileUri, endpoint)
            if (!start.success) return fail(start.message)

            stage = ChainStage.WAITING_FOR_LOCAL_PROXY
            if (!dnsTunnel.awaitSocksReady(endpoint, 20_000)) {
                return fail("Local SOCKS5 listener did not become ready.")
            }

            stage = ChainStage.TESTING_UPSTREAM
            val upstream = dnsTunnel.testEgress(endpoint)
            if (!upstream.tunnelEstablished || !upstream.internetReachable) {
                return fail("Upstream end-to-end test failed.")
            }

            stage = ChainStage.STARTING_SING_BOX
            val tun = singBox.startTunThroughSocks(endpoint)
            if (!tun.success) return fail(tun.message)

            stage = ChainStage.TESTING_FULL_TUNNEL
            val full = singBox.testFullTunnel()
            val health = ChainHealth(
                tunnelProcessAlive = true,
                socksListenerReady = true,
                upstreamReachable = upstream.internetReachable,
                singBoxTunEstablished = full.tunnelEstablished,
                endToEndRequestSucceeded = full.internetReachable,
                dnsLeakCheckPassed = full.detail == "DNS_LEAK_CHECK_PASSED"
            )
            onHealth(health)
            if (!health.isHealthy) return fail("Full-chain health checks failed.")

            stage = ChainStage.CONNECTED
            return EngineResult(true, "DNS tunnel to sing-box chain verified.")
        } catch (error: Throwable) {
            return fail(error.message ?: "Unexpected chain failure.")
        }
    }

    suspend fun disconnect(): EngineResult {
        stage = ChainStage.STOPPING
        val tunResult = runCatching { singBox.stopTun() }
            .getOrElse { EngineResult(false, it.message ?: "sing-box stop failed") }
        val tunnelResult = runCatching { dnsTunnel.stop() }
            .getOrElse { EngineResult(false, it.message ?: "DNS tunnel stop failed") }
        stage = ChainStage.IDLE
        return EngineResult(tunResult.success && tunnelResult.success,
            listOf(tunResult.message, tunnelResult.message).joinToString("; "))
    }

    private suspend fun fail(message: String): EngineResult {
        stage = ChainStage.FAILED
        runCatching { singBox.stopTun() }
        runCatching { dnsTunnel.stop() }
        return EngineResult(false, message)
    }
}
