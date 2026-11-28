package com.mle.vpn.engine

import java.io.IOException

/**
 * Converts engine/platform exceptions into user-safe diagnostics.
 * Exception messages and stack traces may contain profile material and must not
 * be surfaced in notifications, UI state, or routine logs.
 */
internal object MleVpnFailure {
    fun userMessage(error: Throwable): String = when (error) {
        is SecurityException -> "مجوز لازم برای راه‌اندازی یا مدیریت تونل صادر نشده است."
        is IllegalArgumentException -> "ساختار کانفیگ یا تنظیمات تونل معتبر نیست."
        is IOException -> "عملیات شبکه یا منبع تونل با خطا مواجه شد."
        else -> "عملیات VPN ناموفق بود. گزارش فنی بدون اطلاعات محرمانه ثبت شد."
    }

    fun logCategory(error: Throwable): String =
        error.javaClass.simpleName.takeIf { it.isNotBlank() } ?: "UnknownException"
}
