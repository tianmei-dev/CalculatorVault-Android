package com.aurora.calculatorvault.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PasswordHashResult(
    val hash: String,
    val salt: String,
    val algorithm: String,
    val iterations: Int,
)

interface PasswordHasher {
    suspend fun hash(password: CharArray): PasswordHashResult

    suspend fun verify(
        password: CharArray,
        hash: String,
        salt: String,
        algorithm: String,
        iterations: Int,
    ): Boolean
}

class Pbkdf2PasswordHasher(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val saltProvider: () -> ByteArray = {
        ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
    },
) : PasswordHasher {

    override suspend fun hash(password: CharArray): PasswordHashResult = withContext(dispatcher) {
        val salt = saltProvider().also {
            require(it.size >= SALT_BYTES) { "Salt must contain at least $SALT_BYTES bytes" }
        }
        try {
            val hashBytes = derive(password, salt, DEFAULT_ITERATIONS)
            PasswordHashResult(
                hash = Base64.getEncoder().encodeToString(hashBytes),
                salt = Base64.getEncoder().encodeToString(salt),
                algorithm = ALGORITHM,
                iterations = DEFAULT_ITERATIONS,
            )
        } finally {
            password.fill(NULL_CHAR)
        }
    }

    override suspend fun verify(
        password: CharArray,
        hash: String,
        salt: String,
        algorithm: String,
        iterations: Int,
    ): Boolean = withContext(dispatcher) {
        try {
            if (algorithm != ALGORITHM || iterations < MIN_ITERATIONS) return@withContext false
            val expectedHash = Base64.getDecoder().decode(hash)
            val decodedSalt = Base64.getDecoder().decode(salt)
            if (decodedSalt.size < SALT_BYTES) return@withContext false
            val actualHash = derive(password, decodedSalt, iterations)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            password.fill(NULL_CHAR)
        }
    }

    private fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val keySpec = PBEKeySpec(password, salt, iterations, OUTPUT_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 210_000
        const val MIN_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val OUTPUT_BITS = 256
        private const val NULL_CHAR = '\u0000'
    }
}

