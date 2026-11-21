package com.mle.vpn.profile

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MlmServiceRegistryTest {
    @Test
    fun loadsBundledMaeHintsWithoutEnablingAutomaticSteering() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val services = MlmServiceRegistry.load(context.assets)
        assertTrue(services.isNotEmpty())
        assertTrue(services.any { it.id == "youtube" && "com.google.android.youtube" in it.packages })
        assertTrue(services.any { it.id == "telegram" && it.domains.contains("t.me") })
        assertFalse(services.any { it.id.isBlank() })
    }
}
