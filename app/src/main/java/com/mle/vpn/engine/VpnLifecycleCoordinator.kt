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
    private val onStart: suspend (String) -> Unit,
    private val onStop: suspend (Throwable?) -> Unit,
    private val onStartFailure: (Throwable) -> Unit
) {
    private sealed interface Command {
        data class Start(val config: String) : Command
        data class Stop(val initialFailure: Throwable?) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Start -> {
                        try {
                            onStart(command.config)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            onStartFailure(error)
                        }
                    }
                    is Command.Stop -> onStop(command.initialFailure)
                }
            }
        }
    }

    fun enqueueStart(config: String) {
        check(commands.trySend(Command.Start(config)).isSuccess) {
            "VPN lifecycle coordinator is no longer accepting commands."
        }
    }

    fun enqueueStop(initialFailure: Throwable? = null) {
        check(commands.trySend(Command.Stop(initialFailure)).isSuccess) {
            "VPN lifecycle coordinator is no longer accepting commands."
        }
    }
}
