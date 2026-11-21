package com.mle.vpn.engine

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

/**
 * Isolated AmneziaWG process host. AmneziaWG and Xray both embed Go runtimes;
 * loading them in one process is unsafe, so this service and the library's
 * VpnService deliberately share the :awg process.
 */
class AwgHostService : Service() {
    companion object {
        const val MSG_START = 1
        const val MSG_STOP = 2
        const val MSG_REPLY = 100
        const val KEY_CONFIG = "config"
        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        private const val TAG = "MLE-AWG"
    }

    private val worker = HandlerThread("mle-awg-host")
    private lateinit var messenger: Messenger
    private var backend: GoBackend? = null
    private var tunnel: Tunnel? = null

    override fun onCreate() {
        super.onCreate()
        worker.start()
        messenger = Messenger(Handler(worker.looper) { message ->
            handle(message)
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun handle(message: Message) {
        val result = runCatching {
            when (message.what) {
                MSG_START -> {
                    val raw = message.data?.getString(KEY_CONFIG)
                        ?: error("پیکربندی AmneziaWG ارسال نشده است.")
                    bringUp(raw)
                }
                MSG_STOP -> bringDown()
                else -> error("درخواست موتور AmneziaWG ناشناخته است.")
            }
        }
        val reply = Bundle().apply {
            putBoolean(KEY_OK, result.isSuccess)
            result.exceptionOrNull()?.let { error ->
                putString(KEY_ERROR, error.message?.take(180) ?: error.javaClass.simpleName)
            }
        }
        runCatching {
            message.replyTo?.send(Message.obtain(null, MSG_REPLY).apply { data = reply })
        }
        // Keep this host alive after a failed stop so its backend ownership is
        // retained and a later stop request can retry the native shutdown.
        if (message.what == MSG_STOP && result.isSuccess) stopSelf()
    }

    private fun bringUp(raw: String) {
        bringDown()
        val parsed = Config.parse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        val nextBackend = GoBackend(applicationContext)
        val nextTunnel = object : Tunnel {
            override fun getName(): String = "mle-awg"
            override fun onStateChange(newState: Tunnel.State) = Unit
        }
        // Publish ownership before entering native code: if activation fails
        // part-way through, cleanup still has the exact backend/tunnel pair.
        backend = nextBackend
        tunnel = nextTunnel
        try {
            nextBackend.setState(nextTunnel, Tunnel.State.UP, parsed)
            check(nextBackend.getState(nextTunnel) == Tunnel.State.UP) {
                "AmneziaWG تونل را فعال نکرد."
            }
        } catch (error: Exception) {
            try {
                bringDown()
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            Log.w(TAG, "AmneziaWG start failed: ${error.javaClass.simpleName}")
            throw IllegalStateException(
                error.message?.take(160) ?: "راه‌اندازی AmneziaWG ناموفق بود.",
                error
            )
        }
    }

    private fun bringDown() {
        val currentBackend = backend
        val currentTunnel = tunnel
        check((currentBackend == null) == (currentTunnel == null)) {
            "وضعیت مالکیت موتور AmneziaWG ناسازگار است."
        }
        if (currentBackend != null && currentTunnel != null) {
            // Do not clear our references until the backend confirms DOWN.
            // GoBackend.setState(DOWN) owns the VPN-service teardown itself.
            currentBackend.setState(currentTunnel, Tunnel.State.DOWN, null)
            check(currentBackend.getState(currentTunnel) == Tunnel.State.DOWN) {
                "AmneziaWG خاموش‌شدن تونل را تأیید نکرد."
            }
        }
        backend = null
        tunnel = null
    }

    override fun onDestroy() {
        runCatching { bringDown() }.onFailure {
            Log.e(TAG, "AmneziaWG shutdown failed while host was being destroyed", it)
        }
        worker.quitSafely()
        super.onDestroy()
    }
}
