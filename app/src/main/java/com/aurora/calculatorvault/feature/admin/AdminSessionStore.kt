package com.aurora.calculatorvault.feature.admin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AdminSessionStore {
    fun read(): AdminCredential?
    fun write(value: AdminCredential)
    fun clear()
}

/**
 * Encrypted admin session in noBackupFilesDir. Password is never stored.
 * Namespace is independent from the WeChat [com.aurora.calculatorvault.feature.login.session.KeystoreTokenStore].
 */
class KeystoreAdminSessionStore(
    context: Context,
    namespace: String = "admin-session",
) : AdminSessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "$namespace.enc"))
    private val loggedOut = File(context.noBackupFilesDir, "$namespace.logged-out")
    private val alias = "calculator_vault_${namespace}_key_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    @Synchronized
    override fun read(): AdminCredential? {
        if (loggedOut.exists() || !file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val parts = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8).split('\n')
        require(parts.size == 3)
        val username = parts[1]
        require(username.isNotBlank())
        return AdminCredential(
            token = parts[2],
            expiresAt = Instant.parse(parts[0]),
            username = username,
        )
    }

    @Synchronized
    override fun write(value: AdminCredential) {
        require(value.username.isNotBlank())
        require(value.token.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plaintext = "${value.expiresAt}\n${value.username}\n${value.token}".toByteArray(Charsets.UTF_8)
        val bytes = cipher.iv + cipher.doFinal(plaintext)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
            if (loggedOut.exists()) check(loggedOut.delete())
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
    }

    @Synchronized
    override fun clear() {
        try {
            loggedOut.writeText("logged-out")
        } catch (_: Exception) {
            // Key destruction below still invalidates ciphertext.
        }
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
        check(!file.baseFile.exists())
    }
}
