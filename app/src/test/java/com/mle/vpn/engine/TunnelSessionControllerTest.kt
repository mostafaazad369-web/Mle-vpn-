package com.mle.vpn.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelSessionControllerTest {
    private class FakeEngine(
        private val startResult: EngineStartResult = EngineStartResult.Started("session-1"),
        private val validation: EngineValidation = EngineValidation.Valid,
    ) : TunnelEngine {
        override val id = "fake"
        var stopped = false
        override suspend fun validate(configuration: EngineConfiguration) = validation
        override suspend fun start(configuration: EngineConfiguration) = startResult
        override suspend fun stop() { stopped = true }
    }

    private val config = EngineConfiguration("profile-1", "{}")

    @Test fun tunnelActiveIsNotInternetVerified() = runBlocking {
        val controller = TunnelSessionController(FakeEngine())
        assertTrue(controller.connect(config))
        assertEquals(TunnelState.TunnelActive("session-1"), controller.state.value)
        controller.reportInternetVerification(true)
        assertEquals(TunnelState.InternetVerified("session-1"), controller.state.value)
    }

    @Test fun invalidConfigurationNeverStartsEngine() = runBlocking {
        val engine = FakeEngine(validation = EngineValidation.Invalid("bad config"))
        val controller = TunnelSessionController(engine)
        assertFalse(controller.connect(config))
        assertEquals(TunnelState.Error("bad config"), controller.state.value)
        assertFalse(engine.stopped)
    }

    @Test fun disconnectStopsEngineAndResetsState() = runBlocking {
        val engine = FakeEngine()
        val controller = TunnelSessionController(engine)
        assertTrue(controller.connect(config))
        assertTrue(controller.disconnect())
        assertTrue(engine.stopped)
        assertEquals(TunnelState.Disconnected, controller.state.value)
    }
}
