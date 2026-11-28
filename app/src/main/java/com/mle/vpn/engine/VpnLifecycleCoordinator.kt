package com.mle.vpn.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Serializes commands that mutate the VPN engine lifecycle.
 *
 * Commands are enqueued synchronously by Android service callbacks, then
 * processed in FIFO order by one coroutine. This prevents concurrent
 * start/reload and stop/close operations from racing each other.
 */
internal class VpnLifecycleCoordinator(
    scope: CoroutineScope,
    private val onStart: suspend (String, Int) -> Unit,
    private val onStop: suspend (Throwable?, Int?) -> Unit,
    private val onStartFailure: (Throwable, Int) -> Unit
) {
    private sealed interface Command {
        data class Start(val config: String, val startId: Int) : Command
        data class Stop(val initialFailure: Throwable?, val stopStartId: Int?) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Start -> {
                        try {
                            onStart(command.config, command.startId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            onStartFailure(error, command.startId)
                        }
                    }
                    is Command.Stop -> onStop(command.initialFailure, command.stopStartId)
                }
            }
        }
    }

    fun enqueueStart(config: String, startId: Int) {
        check(commands.trySend(Command.Start(config, startId)).isSuccess) {
            "VPN lifecycle coordinator is no longer accepting commands."
        }
    }

    fun enqueueStop(initialFailure: Throwable? = null, stopStartId: Int? = null) {
        check(commands.trySend(Command.Stop(initialFailure, stopStartId)).isSuccess) {
            "VPN lifecycle coordinator is no longer accepting commands."
        }
    }
}
