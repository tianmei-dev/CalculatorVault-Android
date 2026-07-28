package com.aurora.calculatorvault.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val SETTINGS_DATA_STORE_NAME = "calculator_vault_settings"

val Context.vaultPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME,
)

