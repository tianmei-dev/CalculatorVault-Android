package com.aurora.calculatorvault.core.datastore.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aurora.calculatorvault.core.security.PasswordHashResult
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

    suspend fun replacePassword(
        result: PasswordHashResult,
        updatedAt: Long,
    )

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
        dataStore.edit { preferences ->
            preferences[Keys.PasswordHash] = result.hash
            preferences[Keys.PasswordSalt] = result.salt
            preferences[Keys.PasswordAlgorithm] = result.algorithm
            preferences[Keys.PasswordIterations] = result.iterations
            preferences[Keys.PasswordCreatedAt] = createdAt
            preferences[Keys.PasswordConfigured] = true
            preferences[Keys.OnboardingCompleted] = true
        }
    }

    override suspend fun replacePassword(
        result: PasswordHashResult,
        updatedAt: Long,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.PasswordHash] = result.hash
            preferences[Keys.PasswordSalt] = result.salt
            preferences[Keys.PasswordAlgorithm] = result.algorithm
            preferences[Keys.PasswordIterations] = result.iterations
            preferences[Keys.PasswordUpdatedAt] = updatedAt
            preferences[Keys.PasswordConfigured] = true
            preferences[Keys.OnboardingCompleted] = true
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
            preferences[Keys.PasswordConfigured] = false
            preferences[Keys.OnboardingCompleted] = false
        }
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
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }
}
