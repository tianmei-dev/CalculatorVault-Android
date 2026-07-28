package com.aurora.calculatorvault.feature.onboarding.data

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHasher
import com.aurora.calculatorvault.core.security.StoredPasswordMaterialValidator
import com.aurora.calculatorvault.feature.onboarding.domain.PasswordPolicy
import com.aurora.calculatorvault.feature.onboarding.domain.PasswordValidation
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestinationResolver

sealed interface OnboardingFailure {
    data object StartupReadFailed : OnboardingFailure
    data object PrivacySaveFailed : OnboardingFailure
    data object PasswordTooShort : OnboardingFailure
    data object PasswordTooLong : OnboardingFailure
    data object PasswordNonNumeric : OnboardingFailure
    data object PasswordHashFailed : OnboardingFailure
    data object PasswordSaveFailed : OnboardingFailure
}

sealed interface OnboardingResult<out T> {
    data class Success<T>(val value: T) : OnboardingResult<T>
    data class Failure(val error: OnboardingFailure) : OnboardingResult<Nothing>
}

interface OnboardingRepositoryContract {
    suspend fun resolveStartupDestination(): OnboardingResult<StartupDestination>
    suspend fun acceptPrivacy(): OnboardingResult<Unit>
    suspend fun configurePassword(password: CharArray): OnboardingResult<Unit>
}

class OnboardingRepository(
    private val dataSource: SecurityPreferencesDataSource,
    private val passwordHasher: PasswordHasher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : OnboardingRepositoryContract {

    override suspend fun resolveStartupDestination(): OnboardingResult<StartupDestination> {
        return try {
            val preferences = dataSource.read()
            val destination = StartupDestinationResolver.resolve(preferences)
            val hasInconsistentCompletion =
                preferences.privacyAccepted &&
                    preferences.hasAnyPasswordState() &&
                    StoredPasswordMaterialValidator.validate(preferences) == null
            if (hasInconsistentCompletion) {
                dataSource.repairIncompletePasswordSetup()
            }
            OnboardingResult.Success(destination)
        } catch (_: Exception) {
            OnboardingResult.Failure(OnboardingFailure.StartupReadFailed)
        }
    }

    override suspend fun acceptPrivacy(): OnboardingResult<Unit> = try {
        dataSource.acceptPrivacy(
            version = PRIVACY_VERSION,
            acceptedAt = currentTimeMillis(),
        )
        OnboardingResult.Success(Unit)
    } catch (_: Exception) {
        OnboardingResult.Failure(OnboardingFailure.PrivacySaveFailed)
    }

    override suspend fun configurePassword(password: CharArray): OnboardingResult<Unit> {
        val validationFailure = when (PasswordPolicy.validate(password)) {
            PasswordValidation.Valid -> null
            PasswordValidation.TooShort -> OnboardingFailure.PasswordTooShort
            PasswordValidation.TooLong -> OnboardingFailure.PasswordTooLong
            PasswordValidation.NonNumeric -> OnboardingFailure.PasswordNonNumeric
        }
        if (validationFailure != null) {
            password.fill(NULL_CHAR)
            return OnboardingResult.Failure(validationFailure)
        }

        val hashResult = try {
            passwordHasher.hash(password)
        } catch (_: Exception) {
            password.fill(NULL_CHAR)
            return OnboardingResult.Failure(OnboardingFailure.PasswordHashFailed)
        }

        return try {
            dataSource.savePasswordInitialization(
                result = hashResult,
                createdAt = currentTimeMillis(),
            )
            OnboardingResult.Success(Unit)
        } catch (_: Exception) {
            OnboardingResult.Failure(OnboardingFailure.PasswordSaveFailed)
        } finally {
            password.fill(NULL_CHAR)
        }
    }

    companion object {
        const val PRIVACY_VERSION = "1.0"
        private const val NULL_CHAR = '\u0000'
    }
}
