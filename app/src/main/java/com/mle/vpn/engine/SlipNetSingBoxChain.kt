package com.mle.vpn.engine

/**
 * Describes SlipNet as an upstream local SOCKS5 proxy and sing-box as the
 * single Android TUN owner. This is an orchestration contract, not a bundled
 * SlipNet/libbox runtime.
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
    IDLE, STARTING_SLIPNET, WAITING_FOR_LOCAL_PROXY, TESTING_SLIPNET_EGRESS,
    STARTING_SING_BOX, TESTING_FULL_TUNNEL, CONNECTED, STOPPING, FAILED
}

data class ChainHealth(
    val slipNetProcessAlive: Boolean,
    val socksListenerReady: Boolean,
    val upstreamReachable: Boolean,
    val singBoxTunEstablished: Boolean,
    val endToEndRequestSucceeded: Boolean,
    val dnsLeakCheckPassed: Boolean,
    val lastError: String? = null
) {
    val isHealthy: Boolean
        get() = slipNetProcessAlive && socksListenerReady && upstreamReachable &&
            singBoxTunEstablished && endToEndRequestSucceeded && dnsLeakCheckPassed
}

interface SlipNetRuntime {
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

/**
 * Starts SlipNet first, validates its local proxy, then starts sing-box TUN.
 * Failure cleanup is performed in reverse order. CONNECTED is reported only
 * after tunnel, end-to-end, and DNS-leak checks pass.
 */
class SlipNetSingBoxChain(
    private val slipNet: SlipNetRuntime,
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
            stage = ChainStage.STARTING_SLIPNET
            val slipStart = slipNet.start(profileUri, endpoint)
            if (!slipStart.success) return fail(slipStart.message)

            stage = ChainStage.WAITING_FOR_LOCAL_PROXY
            if (!slipNet.awaitSocksReady(endpoint, 20_000)) {
                return fail("SlipNet local SOCKS5 listener did not become ready.")
            }

            stage = ChainStage.TESTING_SLIPNET_EGRESS
            val upstream = slipNet.testEgress(endpoint)
            if (!upstream.tunnelEstablished || !upstream.internetReachable) {
                return fail("SlipNet upstream end-to-end test failed.")
            }

            stage = ChainStage.STARTING_SING_BOX
            val tunStart = singBox.startTunThroughSocks(endpoint)
            if (!tunStart.success) return fail(tunStart.message)

            stage = ChainStage.TESTING_FULL_TUNNEL
            val full = singBox.testFullTunnel()
            val health = ChainHealth(
                slipNetProcessAlive = true,
                socksListenerReady = true,
                upstreamReachable = upstream.internetReachable,
                singBoxTunEstablished = full.tunnelEstablished,
                endToEndRequestSucceeded = full.internetReachable,
                dnsLeakCheckPassed = full.detail == "DNS_LEAK_CHECK_PASSED"
            )
            onHealth(health)
            if (!health.isHealthy) return fail("Full-chain health checks failed.")

            stage = ChainStage.CONNECTED
            return EngineResult(true, "SlipNet to sing-box chain verified.")
        } catch (error: Throwable) {
            return fail(error.message ?: "Unexpected chain failure.")
        }
    }

    suspend fun disconnect(): EngineResult {
        stage = ChainStage.STOPPING
        val singResult = runCatching { singBox.stopTun() }
            .getOrElse { EngineResult(false, it.message ?: "sing-box stop failed") }
        val slipResult = runCatching { slipNet.stop() }
            .getOrElse { EngineResult(false, it.message ?: "SlipNet stop failed") }
        stage = ChainStage.IDLE
        return EngineResult(singResult.success && slipResult.success,
            listOf(singResult.message, slipResult.message).joinToString("; "))
    }

    private suspend fun fail(message: String): EngineResult {
        stage = ChainStage.FAILED
        runCatching { singBox.stopTun() }
        runCatching { slipNet.stop() }
        return EngineResult(false, message)
    }
}
