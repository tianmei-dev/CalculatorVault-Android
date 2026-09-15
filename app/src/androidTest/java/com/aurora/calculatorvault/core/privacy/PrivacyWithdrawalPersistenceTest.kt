package com.aurora.calculatorvault.core.privacy

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.calculatorvault.core.analytics.AnalyticsEvent
import com.aurora.calculatorvault.core.analytics.UmengAnalyticsTracker
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.guide.GuidePreferences
import com.aurora.calculatorvault.core.guide.GuideType
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.feature.calculator.domain.StoredVaultPasswordVerifier
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestinationResolver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyWithdrawalPersistenceTest {
    @Test
    fun persistedWithdrawalSurvivesReopenAndKeepsPasswordAndGuides() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Dedicated temporary store: never modify the user's application store.
        val file = File(context.cacheDir, "withdrawal-${System.nanoTime()}.preferences_pb")
        val firstJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file }
        val source = DataStoreSecurityPreferencesDataSource(store)
        val hasher = Pbkdf2PasswordHasher()
        source.acceptPrivacy("1.0", 123L)
        source.savePasswordInitialization(hasher.hash("0123".toCharArray()), 456L)
        val guides = GuidePreferences(store)
        GuideType.entries.forEach { guides.markShown(it) }
        val before = store.data.first().asMap().filterKeys { !it.name.startsWith("privacy_") }
        source.withdrawPrivacy()
        assertEquals(before, store.data.first().asMap().filterKeys { !it.name.startsWith("privacy_") })
        firstJob.cancelAndJoin()

        val secondJob = SupervisorJob()
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file }
            val restored = DataStoreSecurityPreferencesDataSource(reopened)
            assertEquals(StartupDestination.PrivacyConsent, StartupDestinationResolver.resolve(restored.read()))
            assertNull(restored.read().privacyAcceptedAt)
            restored.acceptPrivacy("1.0", 789L)
            assertEquals(StartupDestination.Calculator, StartupDestinationResolver.resolve(restored.read()))
            assertTrue(StoredVaultPasswordVerifier(restored, hasher).verify("0123".toCharArray()))
            GuideType.entries.forEach { assertFalse(GuidePreferences(reopened).claim(it)) }
        } finally {
            secondJob.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun analyticsDropsEventsBeforeInitAndAfterWithdrawalWithoutReplay() {
        var dispatched = 0
        val tracker = UmengAnalyticsTracker(ApplicationProvider.getApplicationContext()) { _, _ -> dispatched++ }
        tracker.track(AnalyticsEvent.PrivateAlbumClick)
        assertEquals(0, dispatched)
        tracker.markInitialized()
        tracker.track(AnalyticsEvent.PrivateAlbumClick)
        assertEquals(1, dispatched)
        tracker.disable()
        repeat(10) { tracker.track(AnalyticsEvent.PrivateAlbumClick) }
        assertEquals(1, dispatched)
        // Test the gate only: production SDK recovery always uses a fresh process.
        tracker.markInitialized()
        assertEquals(1, dispatched)
    }
}
