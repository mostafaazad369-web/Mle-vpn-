package com.mle.vpn.engine

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MleVpnFailureTest {
    @Test fun userMessagesDoNotContainRawExceptionText() {
        val secret = "vless://uuid:private-secret@example.invalid:443"
        val message = MleVpnFailure.userMessage(IllegalStateException(secret))
        assertFalse(message.contains(secret))
        assertFalse(message.contains("private-secret"))
    }

    @Test fun knownFailureCategoriesAreMappedSafely() {
        assertEquals(
            "مجوز لازم برای راه‌اندازی یا مدیریت تونل صادر نشده است.",
            MleVpnFailure.userMessage(SecurityException("sensitive detail"))
        )
        assertEquals(
            "عملیات شبکه یا منبع تونل با خطا مواجه شد.",
            MleVpnFailure.userMessage(IOException("sensitive detail"))
        )
    }

    @Test fun logCategoryContainsOnlyExceptionType() {
        assertEquals("IllegalStateException", MleVpnFailure.logCategory(IllegalStateException("secret")))
    }
}
