package com.mle.vpn.profile

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProfileVaultLoadResultTest {
    @Test fun emptyVaultIsReportedAsSuccessfulEmptyState() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mle_profiles_v1", Context.MODE_PRIVATE).edit().clear().commit()

        val result = ProfileVault(context).loadAllResult()

        assertTrue(result is ProfileVaultLoadResult.Success)
        assertTrue((result as ProfileVaultLoadResult.Success).profiles.isEmpty())
    }

    @Test fun unreadableCiphertextIsNotReportedAsEmptyVault() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("mle_profiles_v1", Context.MODE_PRIVATE)
        val invalidCiphertext = "not-valid-encrypted-data"
        prefs.edit().putString("encrypted_profiles", invalidCiphertext).commit()

        val result = ProfileVault(context).loadAllResult()

        assertEquals(ProfileVaultLoadResult.Unavailable, result)
        assertEquals(invalidCiphertext, prefs.getString("encrypted_profiles", null))
    }
}
