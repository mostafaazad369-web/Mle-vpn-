package com.mle.vpn.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide single-owner gate for Android's one active VpnService/TUN.
 *
 * All native engines (sing-box, OpenVPN, WireGuard-native, etc.) must acquire
 * this lease before starting and release it only after their packet engine and
 * TUN descriptor have stopped. Android VPN consent is still enforced by the
 * owning VpnService; this gate only prevents competing in-app engines.
 */
enum class MleVpnEngine {
    SING_BOX,
    OPENVPN,
    WIREGUARD_NATIVE,
    OTHER
}

object MleVpnEngineArbiter {
    data class Lease internal constructor(
        val engine: MleVpnEngine,
        internal val generation: Long
    )

    private val sequence = AtomicLong(0)
    @Volatile private var active: Lease? = null

    @Synchronized
    fun acquire(engine: MleVpnEngine): Lease {
        val current = active
        if (current != null) {
            check(current.engine == engine) {
                "VPN engine ${current.engine} already owns the Android tunnel; stop it before starting $engine."
            }
            return current
        }
        return Lease(engine, sequence.incrementAndGet()).also { active = it }
    }

    @Synchronized
    fun release(lease: Lease?) {
        if (lease == null) return
        if (active?.generation == lease.generation && active?.engine == lease.engine) {
            active = null
        }
    }

    @Synchronized
    fun activeEngine(): MleVpnEngine? = active?.engine

    @Synchronized
    internal fun resetForTests() {
        active = null
    }
}
