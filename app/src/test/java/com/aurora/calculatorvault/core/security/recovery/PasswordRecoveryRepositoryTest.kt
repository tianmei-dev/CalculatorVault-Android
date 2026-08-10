package com.aurora.calculatorvault.core.security.recovery

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHashResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoveryRepositoryTest {

    @Test
    fun `reveal returns unavailable when recovery material is missing`() = runBlocking {
        val repository = PasswordRecoveryRepository(
            dataSource = FakeSecurityDataSource(),
            cipher = TestRecoveryCipher(),
        )

        assertEquals(PasswordRecoveryResult.Unavailable, repository.reveal())
    }

    @Test
    fun `store and reveal preserves leading zeros`() = runBlocking {
        val dataSource = FakeSecurityDataSource()
        val repository = PasswordRecoveryRepository(
            dataSource = dataSource,
            cipher = TestRecoveryCipher(),
            currentTimeMillis = { 12L },
        )

        assertTrue(repository.store("001234".toCharArray()))

        val result = repository.reveal() as PasswordRecoveryResult.Success
        assertEquals("001234", result.password.concatToString())
        assertEquals("001234", dataSource.preferences.passwordRecoveryCiphertext)
        assertEquals(12L, dataSource.preferences.passwordRecoveryUpdatedAt)

        result.password.fill('\u0000')
    }

    @Test
    fun `reveal treats non numeric recovered password as corrupted`() = runBlocking {
        val repository = PasswordRecoveryRepository(
            dataSource = FakeSecurityDataSource(
                preferences = recoveryPreferences(ciphertext = "12a4"),
            ),
            cipher = TestRecoveryCipher(),
        )

        assertEquals(PasswordRecoveryResult.Corrupted, repository.reveal())
    }

    @Test
    fun `clear removes recovery material`() = runBlocking {
        val dataSource = FakeSecurityDataSource(
            preferences = recoveryPreferences(ciphertext = "4826"),
        )
        val repository = PasswordRecoveryRepository(
            dataSource = dataSource,
            cipher = TestRecoveryCipher(),
        )

        repository.clear()

        assertEquals(PasswordRecoveryResult.Unavailable, repository.reveal())
    }

    private class TestRecoveryCipher : PasswordRecoveryCipher {
        override suspend fun encrypt(password: CharArray, updatedAt: Long): PasswordRecoveryMaterial =
            PasswordRecoveryMaterial(
                ciphertext = password.concatToString(),
                iv = "test-iv",
                algorithm = "AES/GCM/NoPadding",
                version = 1,
                updatedAt = updatedAt,
            )

        override suspend fun decrypt(material: PasswordRecoveryMaterial): CharArray =
            material.ciphertext.toCharArray()
    }

    private class FakeSecurityDataSource(
        var preferences: SecurityPreferences = SecurityPreferences(),
    ) : SecurityPreferencesDataSource {
        override suspend fun read(): SecurityPreferences = preferences

        override suspend fun acceptPrivacy(version: String, acceptedAt: Long) = Unit

        override suspend fun savePasswordInitialization(
            result: PasswordHashResult,
            createdAt: Long,
        ) = Unit

        override suspend fun replacePassword(result: PasswordHashResult, updatedAt: Long) = Unit

        override suspend fun repairIncompletePasswordSetup() = Unit

        override suspend fun saveRecoveryMaterial(material: PasswordRecoveryMaterial) {
            preferences = preferences.copy(
                passwordRecoveryCiphertext = material.ciphertext,
                passwordRecoveryIv = material.iv,
                passwordRecoveryAlgorithm = material.algorithm,
                passwordRecoveryVersion = material.version,
                passwordRecoveryUpdatedAt = material.updatedAt,
            )
        }

        override suspend fun clearRecoveryMaterial() {
            preferences = preferences.copy(
                passwordRecoveryCiphertext = null,
                passwordRecoveryIv = null,
                passwordRecoveryAlgorithm = null,
                passwordRecoveryVersion = null,
                passwordRecoveryUpdatedAt = null,
            )
        }
    }

    private companion object {
        fun recoveryPreferences(ciphertext: String) = SecurityPreferences(
            passwordRecoveryCiphertext = ciphertext,
            passwordRecoveryIv = "test-iv",
            passwordRecoveryAlgorithm = "AES/GCM/NoPadding",
            passwordRecoveryVersion = 1,
            passwordRecoveryUpdatedAt = 1L,
        )
    }
}
