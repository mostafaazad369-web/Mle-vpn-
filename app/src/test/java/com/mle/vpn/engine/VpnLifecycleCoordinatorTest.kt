package com.mle.vpn.engine

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnLifecycleCoordinatorTest {
    @Test
    fun startStopStartCommandsAreProcessedInFifoOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondFinished = CompletableDeferred<Unit>()

        try {
            val coordinator = VpnLifecycleCoordinator(
                scope = scope,
                onStart = { config ->
                    events += "start:$config"
                    if (config == "first") {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    events += "started:$config"
                    if (config == "second") secondFinished.complete(Unit)
                },
                onStop = {
                    events += "stop"
                },
                onStartFailure = { throw AssertionError("Unexpected start failure", it) }
            )

            coordinator.enqueueStart("first")
            firstStarted.await()
            coordinator.enqueueStop()
            coordinator.enqueueStart("second")
            releaseFirst.complete(Unit)
            secondFinished.await()

            assertEquals(
                listOf("start:first", "started:first", "stop", "start:second", "started:second"),
                events.toList()
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedStartDoesNotPreventFollowingStopCommand() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failureObserved = CompletableDeferred<Unit>()
        val stopObserved = CompletableDeferred<Unit>()

        try {
            val coordinator = VpnLifecycleCoordinator(
                scope = scope,
                onStart = { throw IllegalStateException("private profile data") },
                onStop = { stopObserved.complete(Unit) },
                onStartFailure = { failureObserved.complete(Unit) }
            )

            coordinator.enqueueStart("invalid-config")
            coordinator.enqueueStop()

            failureObserved.await()
            stopObserved.await()
        } finally {
            scope.cancel()
        }
    }
}
