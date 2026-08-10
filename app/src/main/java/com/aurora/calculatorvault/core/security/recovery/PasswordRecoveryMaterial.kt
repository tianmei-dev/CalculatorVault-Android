package com.aurora.calculatorvault.core.security.recovery

data class PasswordRecoveryMaterial(
    val ciphertext: String,
    val iv: String,
    val algorithm: String,
    val version: Int,
    val updatedAt: Long,
)

sealed interface PasswordRecoveryResult {
    data class Success(val password: CharArray) : PasswordRecoveryResult
    data object Unavailable : PasswordRecoveryResult
    data object Corrupted : PasswordRecoveryResult
    data object Failed : PasswordRecoveryResult
}
