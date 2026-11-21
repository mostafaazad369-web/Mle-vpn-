package com.mle.vpn.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentSourceRefreshWorkerTest {
    @Test
    fun onlyTreatsANewOnlineValidationAsSuccessForThisRun() {
        assertTrue(hasNewValidatedSourceFetch(1_000L, 1_001L))
        assertFalse(hasNewValidatedSourceFetch(1_000L, 1_000L))
        assertFalse(hasNewValidatedSourceFetch(1_000L, 0L))
    }
}
