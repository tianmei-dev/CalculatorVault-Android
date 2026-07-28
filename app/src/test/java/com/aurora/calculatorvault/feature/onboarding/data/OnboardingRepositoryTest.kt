package com.aurora.calculatorvault.feature.onboarding.data

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHashResult
import com.aurora.calculatorvault.core.security.PasswordHasher
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRepositoryTest {

    @Test
    fun `privacy acceptance stores current version and timestamp`() = runBlocking {
        val dataSource = FakeSecurityDataSource()
        val repository = repository(dataSource = dataSource, now = 42L)

        assertEquals(OnboardingResult.Success(Unit), repository.acceptPrivacy())
        assertTrue(dataSource.preferences.privacyAccepted)
        assertEquals(OnboardingRepository.PRIVACY_VERSION, dataSource.preferences.privacyVersion)
        assertEquals(42L, dataSource.preferences.privacyAcceptedAt)
    }

    @Test
    fun `valid password is hashed before atomic persistence and input copy is cleared`() =
        runBlocking {
            val dataSource = FakeSecurityDataSource(
                preferences = SecurityPreferences(privacyAccepted = true),
            )
            val input = "4826".toCharArray()
            val repository = repository(dataSource = dataSource, now = 99L)

            assertEquals(
                OnboardingResult.Success(Unit),
                repository.configurePassword(input),
            )
            assertTrue(dataSource.preferences.passwordConfigured)
            assertTrue(dataSource.preferences.onboardingCompleted)
            assertEquals("hash", dataSource.preferences.passwordHash)
            assertEquals(99L, dataSource.preferences.passwordCreatedAt)
            assertTrue(input.all { it == '\u0000' })
        }

    @Test
    fun `password save failure never marks initialization complete`() = runBlocking {
        val dataSource = FakeSecurityDataSource(failSave = true)
        val repository = repository(dataSource = dataSource)

        val result = repository.configurePassword("4826".toCharArray())

        assertEquals(
            OnboardingResult.Failure(OnboardingFailure.PasswordSaveFailed),
            result,
        )
        assertFalse(dataSource.preferences.passwordConfigured)
        assertFalse(dataSource.preferences.onboardingCompleted)
    }

    @Test
    fun `hash failure is reported without attempting persistence`() = runBlocking {
        val dataSource = FakeSecurityDataSource()
        val repository = OnboardingRepository(
            dataSource = dataSource,
            passwordHasher = FakePasswordHasher(failHash = true),
        )

        val result = repository.configurePassword("4826".toCharArray())

        assertEquals(
            OnboardingResult.Failure(OnboardingFailure.PasswordHashFailed),
            result,
        )
        assertEquals(0, dataSource.saveCalls)
    }

    @Test
    fun `inconsistent completion is repaired and routed to password creation`() = runBlocking {
        val dataSource = FakeSecurityDataSource(
            preferences = SecurityPreferences(
                privacyAccepted = true,
                passwordConfigured = true,
                onboardingCompleted = true,
            ),
        )
        val repository = repository(dataSource = dataSource)

        val result = repository.resolveStartupDestination()

        assertEquals(OnboardingResult.Success(StartupDestination.CreatePassword), result)
        assertEquals(1, dataSource.repairCalls)
        assertFalse(dataSource.preferences.onboardingCompleted)
    }

    @Test
    fun `read failure is surfaced to startup`() = runBlocking {
        val repository = repository(dataSource = FakeSecurityDataSource(failRead = true))

        assertEquals(
            OnboardingResult.Failure(OnboardingFailure.StartupReadFailed),
            repository.resolveStartupDestination(),
        )
    }

    private fun repository(
        dataSource: FakeSecurityDataSource,
        now: Long = 1L,
    ) = OnboardingRepository(
        dataSource = dataSource,
        passwordHasher = FakePasswordHasher(),
        currentTimeMillis = { now },
    )

    private class FakePasswordHasher(
        private val failHash: Boolean = false,
    ) : PasswordHasher {
        override suspend fun hash(password: CharArray): PasswordHashResult {
            if (failHash) throw IllegalStateException("simulated hash failure")
            password.fill('\u0000')
            return PasswordHashResult(
                hash = "hash",
                salt = "salt",
                algorithm = Pbkdf2PasswordHasher.ALGORITHM,
                iterations = Pbkdf2PasswordHasher.DEFAULT_ITERATIONS,
            )
        }

        override suspend fun verify(
            password: CharArray,
            hash: String,
            salt: String,
            algorithm: String,
            iterations: Int,
        ): Boolean = false
    }

    private class FakeSecurityDataSource(
        var preferences: SecurityPreferences = SecurityPreferences(),
        private val failRead: Boolean = false,
        private val failSave: Boolean = false,
    ) : SecurityPreferencesDataSource {
        var saveCalls = 0
        var repairCalls = 0

        override suspend fun read(): SecurityPreferences {
            if (failRead) throw IOException("simulated read failure")
            return preferences
        }

        override suspend fun acceptPrivacy(version: String, acceptedAt: Long) {
            preferences = preferences.copy(
                privacyAccepted = true,
                privacyVersion = version,
                privacyAcceptedAt = acceptedAt,
            )
        }

        override suspend fun savePasswordInitialization(
            result: PasswordHashResult,
            createdAt: Long,
        ) {
            saveCalls++
            if (failSave) throw IOException("simulated save failure")
            preferences = preferences.copy(
                passwordConfigured = true,
                passwordHash = result.hash,
                passwordSalt = result.salt,
                passwordAlgorithm = result.algorithm,
                passwordIterations = result.iterations,
                passwordCreatedAt = createdAt,
                onboardingCompleted = true,
            )
        }

        override suspend fun repairIncompletePasswordSetup() {
            repairCalls++
            preferences = preferences.copy(
                passwordConfigured = false,
                passwordHash = null,
                passwordSalt = null,
                passwordAlgorithm = null,
                passwordIterations = null,
                passwordCreatedAt = null,
                onboardingCompleted = false,
            )
        }

        override suspend fun replacePassword(
            result: PasswordHashResult,
            updatedAt: Long,
        ) = error("Not used by onboarding tests")
    }
}
