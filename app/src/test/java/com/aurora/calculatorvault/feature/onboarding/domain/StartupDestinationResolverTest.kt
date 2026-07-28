package com.aurora.calculatorvault.feature.onboarding.domain

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationResolverTest {

    @Test
    fun `privacy not accepted routes to consent`() {
        assertEquals(
            StartupDestination.PrivacyConsent,
            StartupDestinationResolver.resolve(SecurityPreferences()),
        )
    }

    @Test
    fun `accepted privacy without password routes to creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(SecurityPreferences(privacyAccepted = true)),
        )
    }

    @Test
    fun `complete and consistent state routes to calculator`() {
        assertEquals(
            StartupDestination.Calculator,
            StartupDestinationResolver.resolve(completePreferences()),
        )
    }

    @Test
    fun `inconsistent completed state falls back to password creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(
                SecurityPreferences(
                    privacyAccepted = true,
                    passwordConfigured = true,
                    onboardingCompleted = true,
                ),
            ),
        )
    }

    @Test
    fun `invalid Base64 salt falls back to password creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(
                completePreferences().copy(passwordSalt = "not-base64"),
            ),
        )
    }

    @Test
    fun `short salt falls back to password creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(
                completePreferences().copy(
                    passwordSalt = Base64.getEncoder().encodeToString(ByteArray(8)),
                ),
            ),
        )
    }

    @Test
    fun `wrong hash length falls back to password creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(
                completePreferences().copy(
                    passwordHash = Base64.getEncoder().encodeToString(ByteArray(16)),
                ),
            ),
        )
    }

    @Test
    fun `unsupported algorithm falls back to password creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(
                completePreferences().copy(passwordAlgorithm = "unsupported"),
            ),
        )
    }

    @Test
    fun `iterations below minimum fall back to password creation`() {
        assertEquals(
            StartupDestination.CreatePassword,
            StartupDestinationResolver.resolve(
                completePreferences().copy(
                    passwordIterations = Pbkdf2PasswordHasher.MIN_ITERATIONS - 1,
                ),
            ),
        )
    }

    @Test
    fun `privacy rejection takes precedence over complete password material`() {
        assertEquals(
            StartupDestination.PrivacyConsent,
            StartupDestinationResolver.resolve(
                completePreferences().copy(privacyAccepted = false),
            ),
        )
    }

    private fun completePreferences() = SecurityPreferences(
        privacyAccepted = true,
        privacyVersion = "1.0",
        privacyAcceptedAt = 1L,
        passwordConfigured = true,
        passwordHash = Base64.getEncoder().encodeToString(ByteArray(32) { 1 }),
        passwordSalt = Base64.getEncoder().encodeToString(ByteArray(16) { 2 }),
        passwordAlgorithm = Pbkdf2PasswordHasher.ALGORITHM,
        passwordIterations = Pbkdf2PasswordHasher.DEFAULT_ITERATIONS,
        passwordCreatedAt = 2L,
        onboardingCompleted = true,
    )
}
