package com.aurora.calculatorvault.core.guide

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidePreferencesTest {
    @Test
    fun `each guide is claimed independently and only once`() = runTest {
        withPreferences { preferences ->
            assertTrue(preferences.claim(GuideType.APP_LOCK))
            assertFalse(preferences.claim(GuideType.APP_LOCK))
            assertTrue(preferences.claim(GuideType.PRIVATE_ALBUM))
            assertFalse(preferences.claim(GuideType.PRIVATE_ALBUM))
        }
    }

    @Test
    fun `legacy usage suppresses only the matching feature guide`() = runTest {
        withPreferences { preferences ->
            assertFalse(
                preferences.claimWithLegacyUsage(
                    GuideType.APP_LOCK,
                    legacyUsageExists = true,
                ),
            )
            assertTrue(
                preferences.claimWithLegacyUsage(
                    GuideType.PRIVATE_ALBUM,
                    legacyUsageExists = false,
                ),
            )
        }
    }

    @Test
    fun `confirmed shortcut callback creates one pending success guide`() = runTest {
        withPreferences { preferences ->
            preferences.migrateLegacyUsage(
                GuideType.APP_DISGUISE_CREATED,
                legacyUsageExists = false,
            )
            preferences.recordDisguiseShortcutCreated()

            assertTrue(preferences.disguiseCreatedPending.first())
            assertTrue(preferences.claimPendingDisguiseCreated())
            assertFalse(preferences.claimPendingDisguiseCreated())
            assertFalse(preferences.disguiseCreatedPending.first())
        }
    }

    @Test
    fun `existing disguise entries suppress created guide for upgraded user`() = runTest {
        withPreferences { preferences ->
            preferences.migrateLegacyUsage(
                GuideType.APP_DISGUISE_CREATED,
                legacyUsageExists = true,
            )
            preferences.recordDisguiseShortcutCreated()

            assertFalse(preferences.disguiseCreatedPending.first())
            assertFalse(preferences.claimPendingDisguiseCreated())
        }
    }

    private suspend fun withPreferences(block: suspend (GuidePreferences) -> Unit) {
        val file = File(
            System.getProperty("java.io.tmpdir"),
            "guide-preferences-${System.nanoTime()}.preferences_pb",
        )
        val scope = TestScope(UnconfinedTestDispatcher())
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            block(GuidePreferences(store))
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
