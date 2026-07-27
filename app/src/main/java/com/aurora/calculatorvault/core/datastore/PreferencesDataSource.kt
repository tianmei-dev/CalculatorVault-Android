package com.aurora.calculatorvault.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

private const val SETTINGS_DATA_STORE_NAME = "calculator_vault_settings"

val Context.vaultPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME,
)

/**
 * 统一 Preferences DataStore 入口。当前仅暴露只读数据流，不保存密码或私密信息。
 */
class PreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<Preferences> = dataStore.data
}

