package com.aurora.calculatorvault.core.security.recovery

import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PasswordRecoveryCipher {
    suspend fun encrypt(password: CharArray, updatedAt: Long): PasswordRecoveryMaterial
    suspend fun decrypt(material: PasswordRecoveryMaterial): CharArray
}

class AndroidKeystorePasswordRecoveryCipher(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PasswordRecoveryCipher {
    override suspend fun encrypt(
        password: CharArray,
        updatedAt: Long,
    ): PasswordRecoveryMaterial = withContext(dispatcher) {
        val passwordBytes = password.concatToString().toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(passwordBytes)
            PasswordRecoveryMaterial(
                ciphertext = Base64.getEncoder().encodeToString(ciphertext),
                iv = Base64.getEncoder().encodeToString(cipher.iv),
                algorithm = TRANSFORMATION,
                version = VERSION,
                updatedAt = updatedAt,
            )
        } finally {
            password.fill(NULL_CHAR)
            passwordBytes.fill(0)
        }
    }

    override suspend fun decrypt(material: PasswordRecoveryMaterial): CharArray =
        withContext(dispatcher) {
            if (material.algorithm != TRANSFORMATION || material.version != VERSION) {
                throw IllegalArgumentException("Unsupported recovery material")
            }
            val ciphertext = Base64.getDecoder().decode(material.ciphertext)
            val iv = Base64.getDecoder().decode(material.iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            val plaintext = cipher.doFinal(ciphertext)
            try {
                String(plaintext, StandardCharsets.UTF_8).toCharArray()
            } finally {
                plaintext.fill(0)
            }
        }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Recovery key missing")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
            SecureRandom(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "calculator_vault_password_recovery_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = 1
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128
        private const val NULL_CHAR = '\u0000'
    }
}
