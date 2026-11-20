package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TelegramSourceCatalogTest {
    @Test
    fun loadsOneHundredUniqueSafeChannelNames() {
        val app = RuntimeEnvironment.getApplication()
        val sources = TelegramSourceCatalog.load(app.assets)
        assertEquals(100, sources.size)
        assertEquals(100, sources.distinctBy(String::lowercase).size)
        assertTrue(sources.all { it.matches(Regex("[A-Za-z0-9_]{5,64}")) })
    }

    @Test
    fun buildsOnlyHttpsPublicPreviewUrls() {
        assertEquals("https://t.me/s/example_channel", TelegramSourceCatalog.publicPreviewUrl("example_channel"))
        assertTrue(runCatching { TelegramSourceCatalog.publicPreviewUrl("https://evil.example") }.isFailure)
    }
}
