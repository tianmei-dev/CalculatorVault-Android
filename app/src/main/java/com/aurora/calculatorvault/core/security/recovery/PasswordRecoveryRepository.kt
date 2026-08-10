package com.aurora.calculatorvault.core.security.recovery

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import javax.crypto.AEADBadTagException

class PasswordRecoveryRepository(
    private val dataSource: SecurityPreferencesDataSource,
    private val cipher: PasswordRecoveryCipher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun createMaterial(password: CharArray): PasswordRecoveryMaterial =
        cipher.encrypt(password, updatedAt = currentTimeMillis())

    suspend fun store(password: CharArray): Boolean = try {
        val material = createMaterial(password)
        dataSource.saveRecoveryMaterial(material)
        true
    } catch (_: Exception) {
        false
    } finally {
        password.fill(NULL_CHAR)
    }

    suspend fun reveal(): PasswordRecoveryResult {
        val material = try {
            dataSource.read().toRecoveryMaterial() ?: return PasswordRecoveryResult.Unavailable
        } catch (_: Exception) {
            return PasswordRecoveryResult.Failed
        }
        return try {
            val password = cipher.decrypt(material)
            if (password.isEmpty() || password.any { !it.isDigit() }) {
                password.fill(NULL_CHAR)
                PasswordRecoveryResult.Corrupted
            } else {
                PasswordRecoveryResult.Success(password)
            }
        } catch (_: IllegalArgumentException) {
            PasswordRecoveryResult.Corrupted
        } catch (_: AEADBadTagException) {
            PasswordRecoveryResult.Corrupted
        } catch (_: Exception) {
            PasswordRecoveryResult.Failed
        }
    }

    suspend fun hasMaterial(): Boolean = try {
        dataSource.read().toRecoveryMaterial() != null
    } catch (_: Exception) {
        false
    }

    suspend fun clear() {
        runCatching { dataSource.clearRecoveryMaterial() }
    }

    private fun SecurityPreferences.toRecoveryMaterial(): PasswordRecoveryMaterial? {
        val ciphertext = passwordRecoveryCiphertext?.takeIf(String::isNotBlank) ?: return null
        val iv = passwordRecoveryIv?.takeIf(String::isNotBlank) ?: return null
        val algorithm = passwordRecoveryAlgorithm?.takeIf(String::isNotBlank) ?: return null
        val version = passwordRecoveryVersion ?: return null
        val updatedAt = passwordRecoveryUpdatedAt ?: return null
        return PasswordRecoveryMaterial(
            ciphertext = ciphertext,
            iv = iv,
            algorithm = algorithm,
            version = version,
            updatedAt = updatedAt,
        )
    }

    private companion object {
        const val NULL_CHAR = '\u0000'
    }
}
