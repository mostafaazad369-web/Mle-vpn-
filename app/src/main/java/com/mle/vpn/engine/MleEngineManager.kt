package com.mle.vpn.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one active Android VPN data-plane engine.
 *
 * Engines are registered only when their runtime adapter is present. This keeps
 * the UI/capability list honest and prevents a planned native core from being
 * selected as if it were already bundled.
 */
interface MleEngineAdapter {
    val id: String
    val displayName: String
    suspend fun start(config: String)
    suspend fun stop()
}

class SingBoxEngineAdapter(
    private val runtime: MleBoxRuntime
) : MleEngineAdapter {
    override val id: String = "sing-box"
    override val displayName: String = "sing-box"

    override suspend fun start(config: String) = runtime.start(config)
    override suspend fun stop() = runtime.stop()
}

class MleEngineManager(adapters: List<MleEngineAdapter>) {
    private val lock = Mutex()
    private val engines = adapters.associateBy { it.id }
    private var activeEngine: MleEngineAdapter? = null

    init {
        require(engines.isNotEmpty()) { "At least one VPN engine adapter must be registered." }
        require(engines.size == adapters.size) { "Duplicate VPN engine adapter id." }
    }

    fun availableEngines(): List<Pair<String, String>> =
        engines.values.map { it.id to it.displayName }.sortedBy { it.first }

    suspend fun start(engineId: String, config: String) = lock.withLock {
        require(config.isNotBlank()) { "VPN configuration is empty." }
        val next = engines[engineId]
            ?: error("موتور $engineId در این نسخه نصب یا فعال نشده است.")
        if (activeEngine !== next) {
            activeEngine?.stop()
            activeEngine = null
        }
        next.start(config)
        activeEngine = next
    }

    suspend fun stop() = lock.withLock {
        val current = activeEngine ?: return@withLock
        activeEngine = null
        current.stop()
    }

    fun activeEngineId(): String? = activeEngine?.id
}
