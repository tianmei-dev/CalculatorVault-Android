package com.aurora.calculatorvault.core.datastore.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import com.aurora.calculatorvault.core.security.PasswordHashResult
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPreferencesDataSourceTest {

    @Test
    fun `withdrawal changes only consent keys and retains all business preferences`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val source = DataStoreSecurityPreferencesDataSource(store)
        source.acceptPrivacy("1.0", 1234L)
        source.savePasswordInitialization(passwordHashResult(), 5678L)
        store.edit {
            it[booleanPreferencesKey("guide_vault_entry_shown")] = true
            it[stringPreferencesKey("unrelated_business_configuration")] = "preserve-me"
        }
        val before = store.data.first().asMap()
        source.withdrawPrivacy()
        val after = store.data.first().asMap()
        val consentKeys = setOf("privacy_accepted", "privacy_version", "privacy_accepted_at")
        assertEquals(before.filterKeys { it.name !in consentKeys }, after.filterKeys { it.name !in consentKeys })
        val saved = DataStoreSecurityPreferencesDataSource(store).read()
        assertFalse(saved.privacyAccepted)
        assertNull(saved.privacyVersion)
        assertNull(saved.privacyAcceptedAt)
        assertTrue(saved.passwordConfigured)
        assertTrue(saved.onboardingCompleted)
        source.acceptPrivacy("1.0", 9999L)
        assertEquals("encoded-hash", source.read().passwordHash)
    }

    @Test
    fun `withdrawal write failure preserves consent and password`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val source = DataStoreSecurityPreferencesDataSource(store)
        source.acceptPrivacy("1.0", 1234L)
        source.savePasswordInitialization(passwordHashResult(), 5678L)
        val before = store.data.first().asMap()
        store.failUpdates = true
        assertTrue(runCatching { source.withdrawPrivacy() }.isFailure)
        assertEquals(before, store.data.first().asMap())
    }

    @Test
    fun `privacy acceptance saves version and timestamp`() = runBlocking {
        val dataSource = DataStoreSecurityPreferencesDataSource(InMemoryPreferencesDataStore())
        dataSource.acceptPrivacy(version = "1.0", acceptedAt = 1234L)

        val saved = dataSource.read()
        assertTrue(saved.privacyAccepted)
        assertEquals("1.0", saved.privacyVersion)
        assertEquals(1234L, saved.privacyAcceptedAt)
        assertFalse(saved.onboardingCompleted)
    }

    @Test
    fun `password material and completion flags are saved atomically`() = runBlocking {
        val dataSource = DataStoreSecurityPreferencesDataSource(InMemoryPreferencesDataStore())
        dataSource.savePasswordInitialization(
            result = passwordHashResult(),
            createdAt = 5678L,
        )

        val saved = dataSource.read()
        assertEquals("encoded-hash", saved.passwordHash)
        assertEquals("encoded-salt", saved.passwordSalt)
        assertEquals("PBKDF2WithHmacSHA256", saved.passwordAlgorithm)
        assertEquals(210_000, saved.passwordIterations)
        assertEquals(5678L, saved.passwordCreatedAt)
        assertTrue(saved.passwordConfigured)
        assertTrue(saved.onboardingCompleted)
    }

    @Test
    fun `failed password transaction never marks onboarding complete`() = runBlocking {
        val store = InMemoryPreferencesDataStore(failUpdates = true)
        val dataSource = DataStoreSecurityPreferencesDataSource(store)

        runCatching {
            dataSource.savePasswordInitialization(passwordHashResult(), createdAt = 5678L)
        }

        val saved = dataSource.read()
        assertFalse(saved.passwordConfigured)
        assertFalse(saved.onboardingCompleted)
        assertNull(saved.passwordHash)
        assertNull(saved.passwordSalt)
    }

    @Test
    fun `repair removes partial password material but preserves privacy`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val dataSource = DataStoreSecurityPreferencesDataSource(store)
        dataSource.acceptPrivacy(version = "1.0", acceptedAt = 1234L)
        dataSource.savePasswordInitialization(passwordHashResult(), createdAt = 5678L)

        dataSource.repairIncompletePasswordSetup()

        val repaired = dataSource.read()
        assertTrue(repaired.privacyAccepted)
        assertFalse(repaired.passwordConfigured)
        assertFalse(repaired.onboardingCompleted)
        assertNull(repaired.passwordHash)
        assertNull(repaired.passwordSalt)
    }

    @Test
    fun `password replacement updates all material in one transaction and preserves onboarding`() =
        runBlocking {
            val store = InMemoryPreferencesDataStore()
            val dataSource = DataStoreSecurityPreferencesDataSource(store)
            dataSource.acceptPrivacy(version = "1.0", acceptedAt = 1234L)
            dataSource.savePasswordInitialization(passwordHashResult(), createdAt = 5678L)

            dataSource.replacePassword(
                result = passwordHashResult().copy(
                    hash = "new-encoded-hash",
                    salt = "new-encoded-salt",
                ),
                updatedAt = 9012L,
            )

            val saved = dataSource.read()
            assertEquals("new-encoded-hash", saved.passwordHash)
            assertEquals("new-encoded-salt", saved.passwordSalt)
            assertEquals(5678L, saved.passwordCreatedAt)
            assertEquals(9012L, saved.passwordUpdatedAt)
            assertTrue(saved.privacyAccepted)
            assertTrue(saved.passwordConfigured)
            assertTrue(saved.onboardingCompleted)
        }

    private fun passwordHashResult() = PasswordHashResult(
        hash = "encoded-hash",
        salt = "encoded-salt",
        algorithm = "PBKDF2WithHmacSHA256",
        iterations = 210_000,
    )

    private class InMemoryPreferencesDataStore(
        var failUpdates: Boolean = false,
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            if (failUpdates) throw IOException("simulated write failure")
            return transform(state.value).also { state.value = it }
        }
    }
}
