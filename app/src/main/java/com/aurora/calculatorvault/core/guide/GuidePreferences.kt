package com.aurora.calculatorvault.core.guide

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class GuidePreferences(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun claim(type: GuideType): Boolean = runCatching {
        var shouldShow = false
        dataStore.edit { preferences ->
            val key = shownKey(type)
            if (preferences[key] != true) {
                preferences[key] = true
                shouldShow = true
            }
        }
        shouldShow
    }.getOrDefault(false)

    suspend fun claimWithLegacyUsage(
        type: GuideType,
        legacyUsageExists: Boolean,
    ): Boolean = runCatching {
        var shouldShow = false
        dataStore.edit { preferences ->
            val migrationKey = migrationKey(type)
            val shownKey = shownKey(type)
            if (preferences[migrationKey] != true) {
                preferences[migrationKey] = true
                if (legacyUsageExists) preferences[shownKey] = true
            }
            if (preferences[shownKey] != true) {
                preferences[shownKey] = true
                shouldShow = true
            }
        }
        shouldShow
    }.getOrDefault(false)

    suspend fun migrateLegacyUsage(
        type: GuideType,
        legacyUsageExists: Boolean,
    ) {
        runCatching {
            dataStore.edit { preferences ->
                val migrationKey = migrationKey(type)
                if (preferences[migrationKey] != true) {
                    preferences[migrationKey] = true
                    if (legacyUsageExists) preferences[shownKey(type)] = true
                }
            }
        }
    }

    suspend fun markShown(type: GuideType) {
        runCatching {
            dataStore.edit { it[shownKey(type)] = true }
        }
    }

    suspend fun recordDisguiseShortcutCreated() {
        runCatching {
            dataStore.edit { preferences ->
                if (preferences[Keys.DisguiseCreatedShown] != true) {
                    preferences[Keys.DisguiseCreatedPending] = true
                }
            }
        }
    }

    suspend fun claimPendingDisguiseCreated(): Boolean = runCatching {
        var shouldShow = false
        dataStore.edit { preferences ->
            if (
                preferences[Keys.DisguiseCreatedPending] == true &&
                preferences[Keys.DisguiseCreatedShown] != true
            ) {
                preferences[Keys.DisguiseCreatedShown] = true
                shouldShow = true
            }
            preferences[Keys.DisguiseCreatedPending] = false
        }
        shouldShow
    }.getOrDefault(false)

    val disguiseCreatedPending: Flow<Boolean> = dataStore.data
        .map { it[Keys.DisguiseCreatedPending] ?: false }
        .catch { emit(false) }

    private fun shownKey(type: GuideType): Preferences.Key<Boolean> = when (type) {
        GuideType.VAULT_ENTRY -> Keys.VaultEntryShown
        GuideType.APP_LOCK -> Keys.AppLockShown
        GuideType.APP_DISGUISE_INTRO -> Keys.DisguiseIntroShown
        GuideType.APP_DISGUISE_CREATED -> Keys.DisguiseCreatedShown
        GuideType.PRIVATE_ALBUM -> Keys.PrivateAlbumShown
    }

    private fun migrationKey(type: GuideType): Preferences.Key<Boolean> = when (type) {
        GuideType.APP_LOCK -> Keys.AppLockMigrationChecked
        GuideType.APP_DISGUISE_CREATED -> Keys.DisguiseCreatedMigrationChecked
        GuideType.PRIVATE_ALBUM -> Keys.PrivateAlbumMigrationChecked
        GuideType.VAULT_ENTRY,
        GuideType.APP_DISGUISE_INTRO,
        -> error("Legacy migration is not used for $type")
    }

    private object Keys {
        val VaultEntryShown = booleanPreferencesKey("guide_vault_entry_shown")
        val AppLockShown = booleanPreferencesKey("guide_app_lock_shown")
        val DisguiseIntroShown = booleanPreferencesKey("guide_app_disguise_intro_shown")
        val DisguiseCreatedShown = booleanPreferencesKey("guide_app_disguise_created_shown")
        val PrivateAlbumShown = booleanPreferencesKey("guide_private_album_shown")
        val AppLockMigrationChecked = booleanPreferencesKey("guide_app_lock_migration_checked")
        val DisguiseCreatedMigrationChecked =
            booleanPreferencesKey("guide_app_disguise_created_migration_checked")
        val PrivateAlbumMigrationChecked =
            booleanPreferencesKey("guide_private_album_migration_checked")
        val DisguiseCreatedPending = booleanPreferencesKey("guide_app_disguise_created_pending")
    }
}
