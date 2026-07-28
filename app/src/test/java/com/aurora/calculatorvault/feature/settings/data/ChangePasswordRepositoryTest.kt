package com.aurora.calculatorvault.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHashResult
import com.aurora.calculatorvault.core.security.PasswordHasher
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangePasswordRepositoryTest {

    @Test
    fun `correct current password verifies and wrong password fails`() = runBlocking {
        val material = validMaterial()
        val repository = ChangePasswordRepository(
            dataSource = FakeDataSource(preferences = validPreferences(material)),
            passwordHasher = FakeHasher(correctPassword = "4826", hashResult = material),
        )

        assertEquals(
            ChangePasswordResult.Success(Unit),
            repository.verifyCurrentPassword("4826".toCharArray()),
        )
        assertEquals(
            ChangePasswordResult.Failure(ChangePasswordFailure.CurrentPasswordIncorrect),
            repository.verifyCurrentPassword("4827".toCharArray()),
        )
    }

    @Test
    fun `invalid security material blocks verification`() = runBlocking {
        val repository = ChangePasswordRepository(
            dataSource = FakeDataSource(
                preferences = validPreferences(validMaterial()).copy(passwordSalt = "broken"),
            ),
            passwordHasher = FakeHasher(correctPassword = "4826", hashResult = validMaterial()),
        )

        assertEquals(
            ChangePasswordResult.Failure(ChangePasswordFailure.SecurityDataInvalid),
            repository.verifyCurrentPassword("4826".toCharArray()),
        )
    }

    @Test
    fun `successful replacement creates new salt and invalidates old password`() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val dataSource = DataStoreSecurityPreferencesDataSource(store)
        val salts = ArrayDeque(
            listOf(
                ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { 1 },
                ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { 2 },
            ),
        )
        val hasher = Pbkdf2PasswordHasher(saltProvider = { salts.removeFirst() })
        val original = hasher.hash("4826".toCharArray())
        dataSource.acceptPrivacy("1.0", 1L)
        dataSource.savePasswordInitialization(original, createdAt = 2L)
        val repository = ChangePasswordRepository(dataSource, hasher) { 3L }

        assertEquals(
            ChangePasswordResult.Success(Unit),
            repository.replacePassword("7319".toCharArray()),
        )
        val updated = dataSource.read()
        assertNotEquals(original.hash, updated.passwordHash)
        assertNotEquals(original.salt, updated.passwordSalt)
        assertEquals(Pbkdf2PasswordHasher.ALGORITHM, updated.passwordAlgorithm)
        assertEquals(Pbkdf2PasswordHasher.DEFAULT_ITERATIONS, updated.passwordIterations)
        assertEquals(3L, updated.passwordUpdatedAt)
        assertFalse(repository.verifyCurrentPassword("4826".toCharArray()) is ChangePasswordResult.Success)
        assertEquals(
            ChangePasswordResult.Success(Unit),
            repository.verifyCurrentPassword("7319".toCharArray()),
        )
    }

    @Test
    fun `failed atomic replacement preserves old password material`() = runBlocking {
        val original = validMaterial()
        val dataSource = FakeDataSource(
            preferences = validPreferences(original),
            failReplace = true,
        )
        val repository = ChangePasswordRepository(
            dataSource = dataSource,
            passwordHasher = FakeHasher(
                correctPassword = "4826",
                hashResult = validMaterial(hashByte = 7, saltByte = 8),
            ),
        )

        assertEquals(
            ChangePasswordResult.Failure(ChangePasswordFailure.SaveFailed),
            repository.replacePassword("7319".toCharArray()),
        )
        assertEquals(original.hash, dataSource.preferences.passwordHash)
        assertEquals(original.salt, dataSource.preferences.passwordSalt)
        assertTrue(dataSource.preferences.onboardingCompleted)
    }

    @Test
    fun `domain validation rejects short and non numeric passwords`() = runBlocking {
        val repository = ChangePasswordRepository(
            dataSource = FakeDataSource(preferences = validPreferences(validMaterial())),
            passwordHasher = FakeHasher(correctPassword = "4826", hashResult = validMaterial()),
        )

        assertEquals(
            ChangePasswordResult.Failure(ChangePasswordFailure.InvalidLength),
            repository.replacePassword("123".toCharArray()),
        )
        assertEquals(
            ChangePasswordResult.Failure(ChangePasswordFailure.NonNumeric),
            repository.replacePassword("12a4".toCharArray()),
        )
    }

    private fun validMaterial(
        hashByte: Byte = 1,
        saltByte: Byte = 2,
    ) = PasswordHashResult(
        hash = Base64.getEncoder().encodeToString(ByteArray(32) { hashByte }),
        salt = Base64.getEncoder().encodeToString(ByteArray(16) { saltByte }),
        algorithm = Pbkdf2PasswordHasher.ALGORITHM,
        iterations = Pbkdf2PasswordHasher.DEFAULT_ITERATIONS,
    )

    private fun validPreferences(result: PasswordHashResult) = SecurityPreferences(
        privacyAccepted = true,
        passwordConfigured = true,
        passwordHash = result.hash,
        passwordSalt = result.salt,
        passwordAlgorithm = result.algorithm,
        passwordIterations = result.iterations,
        passwordCreatedAt = 1L,
        onboardingCompleted = true,
    )

    private class FakeHasher(
        private val correctPassword: String,
        private val hashResult: PasswordHashResult,
    ) : PasswordHasher {
        override suspend fun hash(password: CharArray): PasswordHashResult {
            password.fill('\u0000')
            return hashResult
        }

        override suspend fun verify(
            password: CharArray,
            hash: String,
            salt: String,
            algorithm: String,
            iterations: Int,
        ): Boolean = try {
            password.concatToString() == correctPassword
        } finally {
            password.fill('\u0000')
        }
    }

    private class FakeDataSource(
        var preferences: SecurityPreferences,
        private val failReplace: Boolean = false,
    ) : SecurityPreferencesDataSource {
        override suspend fun read(): SecurityPreferences = preferences
        override suspend fun acceptPrivacy(version: String, acceptedAt: Long) = Unit
        override suspend fun savePasswordInitialization(
            result: PasswordHashResult,
            createdAt: Long,
        ) = Unit

        override suspend fun replacePassword(result: PasswordHashResult, updatedAt: Long) {
            if (failReplace) throw IOException("simulated replacement failure")
            preferences = preferences.copy(
                passwordHash = result.hash,
                passwordSalt = result.salt,
                passwordAlgorithm = result.algorithm,
                passwordIterations = result.iterations,
                passwordUpdatedAt = updatedAt,
                passwordConfigured = true,
                onboardingCompleted = true,
            )
        }

        override suspend fun repairIncompletePasswordSetup() = Unit
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
