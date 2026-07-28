package com.aurora.calculatorvault.core.security

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `same password and salt produce the same hash`() = runBlocking {
        val salt = ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { it.toByte() }
        val first = Pbkdf2PasswordHasher(saltProvider = { salt.copyOf() })
            .hash(charArrayOf('4', '8', '2', '6'))
        val second = Pbkdf2PasswordHasher(saltProvider = { salt.copyOf() })
            .hash(charArrayOf('4', '8', '2', '6'))

        assertEquals(first.hash, second.hash)
        assertEquals(first.salt, second.salt)
    }

    @Test
    fun `same password with different salts produces different hashes`() = runBlocking {
        val first = Pbkdf2PasswordHasher(
            saltProvider = { ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { 1 } },
        ).hash(charArrayOf('4', '8', '2', '6'))
        val second = Pbkdf2PasswordHasher(
            saltProvider = { ByteArray(Pbkdf2PasswordHasher.SALT_BYTES) { 2 } },
        ).hash(charArrayOf('4', '8', '2', '6'))

        assertNotEquals(first.hash, second.hash)
        assertNotEquals(first.salt, second.salt)
    }

    @Test
    fun `correct password verifies and wrong password fails`() = runBlocking {
        val hasher = Pbkdf2PasswordHasher()
        val result = hasher.hash(charArrayOf('7', '3', '9', '1'))

        assertTrue(
            hasher.verify(
                password = charArrayOf('7', '3', '9', '1'),
                hash = result.hash,
                salt = result.salt,
                algorithm = result.algorithm,
                iterations = result.iterations,
            ),
        )
        assertFalse(
            hasher.verify(
                password = charArrayOf('7', '3', '9', '2'),
                hash = result.hash,
                salt = result.salt,
                algorithm = result.algorithm,
                iterations = result.iterations,
            ),
        )
    }

    @Test
    fun `hash metadata and salt meet security requirements`() = runBlocking {
        val plain = "6842"
        val password = plain.toCharArray()
        val result = Pbkdf2PasswordHasher().hash(password)

        assertNotEquals(plain, result.hash)
        assertEquals(Pbkdf2PasswordHasher.ALGORITHM, result.algorithm)
        assertEquals(Pbkdf2PasswordHasher.DEFAULT_ITERATIONS, result.iterations)
        assertEquals(
            Pbkdf2PasswordHasher.SALT_BYTES,
            Base64.getDecoder().decode(result.salt).size,
        )
        assertEquals(32, Base64.getDecoder().decode(result.hash).size)
        assertTrue(password.all { it == '\u0000' })
    }
}

