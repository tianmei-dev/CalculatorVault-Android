package com.aurora.calculatorvault.feature.calculator.domain

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.aurora.calculatorvault.core.security.PasswordHasher
import com.aurora.calculatorvault.core.security.StoredPasswordMaterialValidator

interface VaultPasswordVerifier {
    suspend fun verify(candidate: CharArray): Boolean
}

class StoredVaultPasswordVerifier(
    private val dataSource: SecurityPreferencesDataSource,
    private val passwordHasher: PasswordHasher,
) : VaultPasswordVerifier {
    override suspend fun verify(candidate: CharArray): Boolean = try {
        val material = StoredPasswordMaterialValidator.validate(dataSource.read()) ?: return false
        passwordHasher.verify(
            password = candidate,
            hash = material.hash,
            salt = material.salt,
            algorithm = material.algorithm,
            iterations = material.iterations,
        )
    } catch (_: Exception) {
        false
    } finally {
        candidate.fill(NULL_CHAR)
    }

    private companion object {
        const val NULL_CHAR = '\u0000'
    }
}

class VaultUnlockUseCase(
    private val verifier: VaultPasswordVerifier,
) {
    suspend fun verify(candidate: CharArray): Boolean {
        if (candidate.size !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH || candidate.any { !it.isDigit() }) {
            candidate.fill(NULL_CHAR)
            return false
        }
        return try {
            verifier.verify(candidate)
        } catch (_: Exception) {
            false
        } finally {
            candidate.fill(NULL_CHAR)
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 4
        const val MAX_PASSWORD_LENGTH = 8
        const val NULL_CHAR = '\u0000'
    }
}

/**
 * 只接受用户直接输入的原始纯数字；计算结果、表达式、小数、负数和百分比均不参与验证。
 */
fun isUnlockCandidate(
    state: CalculatorState,
    rawInput: CharArray,
): Boolean =
    rawInput.size in 4..8 &&
        rawInput.all(Char::isDigit) &&
        isDirectUnlockInputState(state)

fun isDirectUnlockInputState(state: CalculatorState): Boolean =
    state.displayValue.isNotEmpty() &&
        state.displayValue.all(Char::isDigit) &&
        state.expression.isEmpty() &&
        state.previousValue == null &&
        state.pendingOperator == null &&
        !state.isAwaitingOperand &&
        !state.isResultShown &&
        state.repeatOperator == null &&
        state.repeatOperand == null &&
        state.error == null
