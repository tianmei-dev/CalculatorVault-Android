package com.aurora.calculatorvault.feature.login.session

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

interface TokenStore {
    fun read(): SessionCredential?
    fun write(value: SessionCredential)
    fun clear()
}
/** Ciphertext lives exclusively in noBackupFilesDir. No identity or authorization code is saved. */
class KeystoreTokenStore(context: Context, namespace: String = "account-session") : TokenStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "$namespace.enc"))
    private val loggedOut = File(context.noBackupFilesDir, "$namespace.logged-out")
    private val alias = if (namespace == "account-session") "calculator_vault_account_session_v1" else "calculator_vault_${namespace}_key_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(): SessionCredential? {
        if (loggedOut.exists() || !file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val parts = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8).split('\n')
        require(parts.size == 2)
        return SessionCredential(parts[1], Instant.parse(parts[0]))
    }
    @Synchronized override fun write(value: SessionCredential) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal("${value.expiresAt}\n${value.token}".toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(bytes); file.finishWrite(output)
            if (loggedOut.exists()) check(loggedOut.delete())
        }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
    @Synchronized override fun clear() {
        // A durable tombstone prevents cold-start recovery even if ciphertext deletion fails.
        try { loggedOut.writeText("logged-out") } catch (_: Exception) { /* key destruction below */ }
        file.delete()
        // Deleting the key also makes any undeletable ciphertext unusable.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        check(!file.baseFile.exists())
    }
}
