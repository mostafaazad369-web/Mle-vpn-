package com.mle.vpn.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MleEngineManagerTest {
    private class FakeEngine(override val id: String) : MleEngineAdapter {
        override val displayName = id
        val events = mutableListOf<String>()
        override suspend fun start(config: String) { events += "start:$config" }
        override suspend fun stop() { events += "stop" }
    }

    @Test
    fun managerStartsOnlyRegisteredEngineAndStopsPreviousEngine() = runBlocking {
        val first = FakeEngine("sing-box")
        val second = FakeEngine("xray")
        val manager = MleEngineManager(listOf(first, second))
        manager.start("sing-box", "one")
        manager.start("xray", "two")
        assertEquals(listOf("start:one", "stop"), first.events)
        assertEquals(listOf("start:two"), second.events)
        assertEquals("xray", manager.activeEngineId())
        manager.stop()
        assertNull(manager.activeEngineId())
        assertEquals(listOf("start:two", "stop"), second.events)
    }

    @Test
    fun managerRejectsUnregisteredEngine() = runBlocking {
        val manager = MleEngineManager(listOf(FakeEngine("sing-box")))
        val result = runCatching { manager.start("xray", "{}") }
        assertTrue(result.isFailure)
    }

    @Test
    fun managerRejectsBlankConfiguration() = runBlocking {
        val manager = MleEngineManager(listOf(FakeEngine("sing-box")))
        assertTrue(runCatching { manager.start("sing-box", " ") }.isFailure)
    }
}

@RunWith(RobolectricTestRunner::class)
class MleRouteHealthStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recordsSuccessAndLatencyWithoutStoringProfilePayload() {
        val store = MleRouteHealthStore(context)
        store.recordSuccess("private-profile-id", 120)
        val health = store.read("private-profile-id")
        assertEquals(1, health.attempts)
        assertEquals(1, health.successes)
        assertEquals(0, health.consecutiveFailures)
        assertEquals(120.0, health.latencyEwmaMs!!, 0.001)
        val allPrefs = context.getSharedPreferences("mle_route_health_v1", Context.MODE_PRIVATE).all
        assertTrue(allPrefs.values.none { it.toString().contains("private-profile-id") })
    }

    @Test
    fun repeatedFailuresIncreaseCircuitPenaltyAndSuccessResetsIt() {
        val store = MleRouteHealthStore(context)
        repeat(3) { store.recordFailure("route-a") }
        assertEquals(3, store.read("route-a").consecutiveFailures)
        store.recordSuccess("route-a", 90)
        assertEquals(0, store.read("route-a").consecutiveFailures)
    }

    @Test
    fun rankingPrefersPreviouslyHealthyRoute() {
        val store = MleRouteHealthStore(context)
        repeat(4) { store.recordSuccess("fast", 80) }
        repeat(3) { store.recordFailure("slow") }
        assertEquals(listOf("fast", "slow"), store.rank(listOf("slow", "fast")))
    }
}
