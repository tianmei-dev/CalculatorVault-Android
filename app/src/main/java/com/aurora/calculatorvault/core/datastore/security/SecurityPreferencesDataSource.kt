package com.aurora.calculatorvault.core.datastore.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aurora.calculatorvault.core.security.PasswordHashResult
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryMaterial
import kotlinx.coroutines.flow.first

interface SecurityPreferencesDataSource {
    suspend fun read(): SecurityPreferences

    suspend fun acceptPrivacy(
        version: String,
        acceptedAt: Long,
    )

    suspend fun savePasswordInitialization(
        result: PasswordHashResult,
        createdAt: Long,
    )

    suspend fun savePasswordInitialization(
        result: PasswordHashResult,
        createdAt: Long,
        recoveryMaterial: PasswordRecoveryMaterial?,
    ) {
        savePasswordInitialization(result, createdAt)
        recoveryMaterial?.let { saveRecoveryMaterial(it) }
    }

    suspend fun replacePassword(
        result: PasswordHashResult,
        updatedAt: Long,
    )

    suspend fun replacePassword(
        result: PasswordHashResult,
        updatedAt: Long,
        recoveryMaterial: PasswordRecoveryMaterial?,
    ) {
        replacePassword(result, updatedAt)
        if (recoveryMaterial != null) {
            saveRecoveryMaterial(recoveryMaterial)
        } else {
            clearRecoveryMaterial()
        }
    }

    suspend fun saveRecoveryMaterial(material: PasswordRecoveryMaterial) {
        throw UnsupportedOperationException("Recovery material is not supported")
    }

    suspend fun clearRecoveryMaterial() {
        throw UnsupportedOperationException("Recovery material is not supported")
    }

    suspend fun repairIncompletePasswordSetup()
}

class DataStoreSecurityPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) : SecurityPreferencesDataSource {

    override suspend fun read(): SecurityPreferences {
        val preferences = dataStore.data.first()
        return SecurityPreferences(
            privacyAccepted = preferences[Keys.PrivacyAccepted] ?: false,
            privacyVersion = preferences[Keys.PrivacyVersion],
            privacyAcceptedAt = preferences[Keys.PrivacyAcceptedAt],
            passwordConfigured = preferences[Keys.PasswordConfigured] ?: false,
            passwordHash = preferences[Keys.PasswordHash],
            passwordSalt = preferences[Keys.PasswordSalt],
            passwordAlgorithm = preferences[Keys.PasswordAlgorithm],
            passwordIterations = preferences[Keys.PasswordIterations],
            passwordCreatedAt = preferences[Keys.PasswordCreatedAt],
            passwordUpdatedAt = preferences[Keys.PasswordUpdatedAt],
            passwordRecoveryCiphertext = preferences[Keys.PasswordRecoveryCiphertext],
            passwordRecoveryIv = preferences[Keys.PasswordRecoveryIv],
            passwordRecoveryAlgorithm = preferences[Keys.PasswordRecoveryAlgorithm],
            passwordRecoveryVersion = preferences[Keys.PasswordRecoveryVersion],
            passwordRecoveryUpdatedAt = preferences[Keys.PasswordRecoveryUpdatedAt],
            onboardingCompleted = preferences[Keys.OnboardingCompleted] ?: false,
        )
    }

    override suspend fun acceptPrivacy(
        version: String,
        acceptedAt: Long,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.PrivacyAccepted] = true
            preferences[Keys.PrivacyVersion] = version
            preferences[Keys.PrivacyAcceptedAt] = acceptedAt
        }
    }

    override suspend fun savePasswordInitialization(
        result: PasswordHashResult,
        createdAt: Long,
    ) {
        savePasswordInitialization(result, createdAt, recoveryMaterial = null)
    }

    override suspend fun savePasswordInitialization(
        result: PasswordHashResult,
        createdAt: Long,
        recoveryMaterial: PasswordRecoveryMaterial?,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.PasswordHash] = result.hash
            preferences[Keys.PasswordSalt] = result.salt
            preferences[Keys.PasswordAlgorithm] = result.algorithm
            preferences[Keys.PasswordIterations] = result.iterations
            preferences[Keys.PasswordCreatedAt] = createdAt
            if (recoveryMaterial != null) {
                preferences.putRecoveryMaterial(recoveryMaterial)
            } else {
                preferences.clearRecoveryMaterial()
            }
            preferences[Keys.PasswordConfigured] = true
            preferences[Keys.OnboardingCompleted] = true
        }
    }

    override suspend fun replacePassword(
        result: PasswordHashResult,
        updatedAt: Long,
    ) {
        replacePassword(result, updatedAt, recoveryMaterial = null)
    }

    override suspend fun replacePassword(
        result: PasswordHashResult,
        updatedAt: Long,
        recoveryMaterial: PasswordRecoveryMaterial?,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.PasswordHash] = result.hash
            preferences[Keys.PasswordSalt] = result.salt
            preferences[Keys.PasswordAlgorithm] = result.algorithm
            preferences[Keys.PasswordIterations] = result.iterations
            preferences[Keys.PasswordUpdatedAt] = updatedAt
            if (recoveryMaterial != null) {
                preferences.putRecoveryMaterial(recoveryMaterial)
            } else {
                preferences.clearRecoveryMaterial()
            }
            preferences[Keys.PasswordConfigured] = true
            preferences[Keys.OnboardingCompleted] = true
        }
    }

    override suspend fun saveRecoveryMaterial(material: PasswordRecoveryMaterial) {
        dataStore.edit { preferences ->
            preferences.putRecoveryMaterial(material)
        }
    }

    override suspend fun clearRecoveryMaterial() {
        dataStore.edit { preferences ->
            preferences.clearRecoveryMaterial()
        }
    }

    override suspend fun repairIncompletePasswordSetup() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.PasswordHash)
            preferences.remove(Keys.PasswordSalt)
            preferences.remove(Keys.PasswordAlgorithm)
            preferences.remove(Keys.PasswordIterations)
            preferences.remove(Keys.PasswordCreatedAt)
            preferences.remove(Keys.PasswordUpdatedAt)
            preferences.clearRecoveryMaterial()
            preferences[Keys.PasswordConfigured] = false
            preferences[Keys.OnboardingCompleted] = false
        }
    }

    private fun MutablePreferences.putRecoveryMaterial(material: PasswordRecoveryMaterial) {
        this[Keys.PasswordRecoveryCiphertext] = material.ciphertext
        this[Keys.PasswordRecoveryIv] = material.iv
        this[Keys.PasswordRecoveryAlgorithm] = material.algorithm
        this[Keys.PasswordRecoveryVersion] = material.version
        this[Keys.PasswordRecoveryUpdatedAt] = material.updatedAt
    }

    private fun MutablePreferences.clearRecoveryMaterial() {
        remove(Keys.PasswordRecoveryCiphertext)
        remove(Keys.PasswordRecoveryIv)
        remove(Keys.PasswordRecoveryAlgorithm)
        remove(Keys.PasswordRecoveryVersion)
        remove(Keys.PasswordRecoveryUpdatedAt)
    }

    private object Keys {
        val PrivacyAccepted = booleanPreferencesKey("privacy_accepted")
        val PrivacyVersion = stringPreferencesKey("privacy_version")
        val PrivacyAcceptedAt = longPreferencesKey("privacy_accepted_at")
        val PasswordConfigured = booleanPreferencesKey("password_configured")
        val PasswordHash = stringPreferencesKey("password_hash")
        val PasswordSalt = stringPreferencesKey("password_salt")
        val PasswordAlgorithm = stringPreferencesKey("password_algorithm")
        val PasswordIterations = intPreferencesKey("password_iterations")
        val PasswordCreatedAt = longPreferencesKey("password_created_at")
        val PasswordUpdatedAt = longPreferencesKey("password_updated_at")
        val PasswordRecoveryCiphertext = stringPreferencesKey("password_recovery_ciphertext")
        val PasswordRecoveryIv = stringPreferencesKey("password_recovery_iv")
        val PasswordRecoveryAlgorithm = stringPreferencesKey("password_recovery_algorithm")
        val PasswordRecoveryVersion = intPreferencesKey("password_recovery_version")
        val PasswordRecoveryUpdatedAt = longPreferencesKey("password_recovery_updated_at")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }
}
