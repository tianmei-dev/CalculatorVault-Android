package com.aurora.calculatorvault.feature.calculator.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepository
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultUnlockPasswordChangeIntegrationTest {

    @Test
    fun `password replacement immediately updates calculator unlock verification`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val dataSource = DataStoreSecurityPreferencesDataSource(store)
        val salts = ArrayDeque(
            listOf(
                ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { 1 },
                ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { 2 },
            ),
        )
        val hasher = Pbkdf2PasswordHasher(saltProvider = { salts.removeFirst() })
        dataSource.acceptPrivacy(version = "1.0", acceptedAt = 1L)
        dataSource.savePasswordInitialization(
            result = hasher.hash("4826".toCharArray()),
            createdAt = 2L,
        )
        val unlock = VaultUnlockUseCase(StoredVaultPasswordVerifier(dataSource, hasher))
        val changePassword = ChangePasswordRepository(dataSource, hasher) { 3L }

        assertTrue(unlock.verify("4826".toCharArray()))
        assertEquals(
            ChangePasswordResult.Success(Unit),
            changePassword.replacePassword("7319".toCharArray()),
        )
        assertFalse(unlock.verify("4826".toCharArray()))
        assertTrue(unlock.verify("7319".toCharArray()))
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
