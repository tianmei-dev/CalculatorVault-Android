package com.aurora.calculatorvault.feature.settings.data

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHasher
import com.aurora.calculatorvault.core.security.StoredPasswordMaterialValidator
import com.aurora.calculatorvault.feature.onboarding.domain.PasswordPolicy
import com.aurora.calculatorvault.feature.onboarding.domain.PasswordValidation

sealed interface ChangePasswordFailure {
    data object InvalidLength : ChangePasswordFailure
    data object NonNumeric : ChangePasswordFailure
    data object CurrentPasswordIncorrect : ChangePasswordFailure
    data object SecurityDataInvalid : ChangePasswordFailure
    data object VerificationFailed : ChangePasswordFailure
    data object HashFailed : ChangePasswordFailure
    data object SaveFailed : ChangePasswordFailure
}

sealed interface ChangePasswordResult<out T> {
    data class Success<T>(val value: T) : ChangePasswordResult<T>
    data class Failure(val error: ChangePasswordFailure) : ChangePasswordResult<Nothing>
}

interface ChangePasswordRepositoryContract {
    suspend fun verifyCurrentPassword(password: CharArray): ChangePasswordResult<Unit>
    suspend fun replacePassword(newPassword: CharArray): ChangePasswordResult<Unit>
}

class ChangePasswordRepository(
    private val dataSource: SecurityPreferencesDataSource,
    private val passwordHasher: PasswordHasher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ChangePasswordRepositoryContract {

    override suspend fun verifyCurrentPassword(password: CharArray): ChangePasswordResult<Unit> {
        validate(password)?.let {
            password.fill(NULL_CHAR)
            return ChangePasswordResult.Failure(it)
        }
        val preferences = try {
            dataSource.read()
        } catch (_: Exception) {
            password.fill(NULL_CHAR)
            return ChangePasswordResult.Failure(ChangePasswordFailure.VerificationFailed)
        }
        val material = StoredPasswordMaterialValidator.validate(preferences)
        if (material == null) {
            password.fill(NULL_CHAR)
            return ChangePasswordResult.Failure(ChangePasswordFailure.SecurityDataInvalid)
        }

        val verified = try {
            passwordHasher.verify(
                password = password,
                hash = material.hash,
                salt = material.salt,
                algorithm = material.algorithm,
                iterations = material.iterations,
            )
        } catch (_: Exception) {
            password.fill(NULL_CHAR)
            return ChangePasswordResult.Failure(ChangePasswordFailure.VerificationFailed)
        }
        return if (verified) {
            ChangePasswordResult.Success(Unit)
        } else {
            ChangePasswordResult.Failure(ChangePasswordFailure.CurrentPasswordIncorrect)
        }
    }

    override suspend fun replacePassword(newPassword: CharArray): ChangePasswordResult<Unit> {
        validate(newPassword)?.let {
            newPassword.fill(NULL_CHAR)
            return ChangePasswordResult.Failure(it)
        }
        val hashResult = try {
            passwordHasher.hash(newPassword)
        } catch (_: Exception) {
            newPassword.fill(NULL_CHAR)
            return ChangePasswordResult.Failure(ChangePasswordFailure.HashFailed)
        }

        return try {
            dataSource.replacePassword(
                result = hashResult,
                updatedAt = currentTimeMillis(),
            )
            ChangePasswordResult.Success(Unit)
        } catch (_: Exception) {
            ChangePasswordResult.Failure(ChangePasswordFailure.SaveFailed)
        } finally {
            newPassword.fill(NULL_CHAR)
        }
    }

    private fun validate(password: CharArray): ChangePasswordFailure? =
        when (PasswordPolicy.validate(password)) {
            PasswordValidation.Valid -> null
            PasswordValidation.TooShort,
            PasswordValidation.TooLong,
            -> ChangePasswordFailure.InvalidLength
            PasswordValidation.NonNumeric -> ChangePasswordFailure.NonNumeric
        }

    private companion object {
        const val NULL_CHAR = '\u0000'
    }
}
