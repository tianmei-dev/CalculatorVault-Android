package com.aurora.calculatorvault.core.security

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import java.util.Base64

data class StoredPasswordMaterial(
    val hash: String,
    val salt: String,
    val algorithm: String,
    val iterations: Int,
)

object StoredPasswordMaterialValidator {
    fun validate(preferences: SecurityPreferences): StoredPasswordMaterial? {
        if (!preferences.passwordConfigured || !preferences.onboardingCompleted) return null

        val hash = preferences.passwordHash?.takeIf(String::isNotBlank) ?: return null
        val salt = preferences.passwordSalt?.takeIf(String::isNotBlank) ?: return null
        val algorithm = preferences.passwordAlgorithm ?: return null
        val iterations = preferences.passwordIterations ?: return null
        if (
            algorithm != Pbkdf2PasswordHasher.ALGORITHM ||
            iterations < Pbkdf2PasswordHasher.MIN_ITERATIONS
        ) {
            return null
        }

        val decodedHash: ByteArray
        val decodedSalt: ByteArray
        try {
            decodedHash = Base64.getDecoder().decode(hash)
            decodedSalt = Base64.getDecoder().decode(salt)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (
            decodedHash.size != Pbkdf2PasswordHasher.OUTPUT_BITS / Byte.SIZE_BITS ||
            decodedSalt.size < Pbkdf2PasswordHasher.SALT_BYTES
        ) {
            return null
        }

        return StoredPasswordMaterial(
            hash = hash,
            salt = salt,
            algorithm = algorithm,
            iterations = iterations,
        )
    }
}
