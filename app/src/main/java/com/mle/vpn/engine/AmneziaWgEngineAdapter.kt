package com.mle.vpn.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Main-process adapter for the isolated :awg process. The raw .conf contains
 * private keys and is sent only over the app's private Messenger binding.
 */
class AmneziaWgEngineAdapter(context: Context) : MleEngineAdapter {
    private val appContext = context.applicationContext
    override val id: String = "amneziawg"
    override val displayName: String = "AmneziaWG"

    override suspend fun start(config: String) {
        require(config.isNotBlank()) { "پیکربندی AmneziaWG خالی است." }
        request(AwgHostService.MSG_START, config)
    }

    override suspend fun stop() {
        request(AwgHostService.MSG_STOP, null)
    }

    private suspend fun request(action: Int, config: String?) = withContext(Dispatchers.IO) {
        val connected = CountDownLatch(1)
        val replied = CountDownLatch(1)
        @Volatile var remote: Messenger? = null
        @Volatile var responseOk = false
        @Volatile var responseError: String? = null
        @Volatile var bindError: String? = null

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            val data = message.data
            responseOk = data.getBoolean(AwgHostService.KEY_OK)
            responseError = data.getString(AwgHostService.KEY_ERROR)
            replied.countDown()
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: android.os.IBinder) {
                remote = Messenger(binder)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                bindError = "ارتباط با پردازش AmneziaWG قطع شد."
                connected.countDown()
                replied.countDown()
            }
            override fun onBindingDied(name: ComponentName) {
                bindError = "سرویس AmneziaWG از دسترس خارج شد."
                connected.countDown()
                replied.countDown()
            }
            override fun onNullBinding(name: ComponentName) {
                bindError = "سرویس AmneziaWG اتصال معتبری ارائه نکرد."
                connected.countDown()
                replied.countDown()
            }
        }
        val intent = Intent(appContext, AwgHostService::class.java)
        var bound = false
        try {
            appContext.startService(intent)
                ?: error("سرویس AmneziaWG شروع نشد.")
            bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            check(bound) { "اتصال به سرویس AmneziaWG ممکن نشد." }
            check(connected.await(8, TimeUnit.SECONDS)) {
                "پردازش AmneziaWG در زمان مقرر آماده نشد."
            }
            bindError?.let { error(it) }
            val request = Message.obtain(null, action).apply {
                data = Bundle().apply { if (config != null) putString(AwgHostService.KEY_CONFIG, config) }
                replyTo = replyMessenger
            }
            checkNotNull(remote) { "کانال ارتباطی AmneziaWG آماده نیست." }.send(request)
            check(replied.await(35, TimeUnit.SECONDS)) {
                "پاسخ موتور AmneziaWG در زمان مقرر دریافت نشد."
            }
            bindError?.let { error(it) }
            check(responseOk) { responseError ?: "عملیات AmneziaWG ناموفق بود." }
        } catch (error: Exception) {
            Log.w("MLE-AWG", "AWG IPC operation failed: ${error.javaClass.simpleName}")
            throw error
        } finally {
            if (bound) runCatching { appContext.unbindService(connection) }
        }
    }
}
