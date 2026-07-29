package com.aurora.calculatorvault.feature.hiddenapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppLayoutMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class HiddenAppPreferenceState(
    val layoutMode: HiddenAppLayoutMode = HiddenAppLayoutMode.Grid,
    val sortMode: HiddenAppSortMode = HiddenAppSortMode.Manual,
    val usageNoticeDismissed: Boolean = false,
    val hasSeenBatchTip: Boolean = false,
    val hasSeenSortTip: Boolean = false,
)

interface HiddenAppPreferences {
    val state: Flow<HiddenAppPreferenceState>
    suspend fun setLayoutMode(mode: HiddenAppLayoutMode)
    suspend fun setSortMode(mode: HiddenAppSortMode)
    suspend fun dismissUsageNotice()
    suspend fun markBatchTipSeen()
    suspend fun markSortTipSeen()
}

object DefaultHiddenAppPreferences : HiddenAppPreferences {
    override val state = flowOf(HiddenAppPreferenceState())
    override suspend fun setLayoutMode(mode: HiddenAppLayoutMode) = Unit
    override suspend fun setSortMode(mode: HiddenAppSortMode) = Unit
    override suspend fun dismissUsageNotice() = Unit
    override suspend fun markBatchTipSeen() = Unit
    override suspend fun markSortTipSeen() = Unit
}

class DataStoreHiddenAppPreferences(
    private val dataStore: DataStore<Preferences>,
) : HiddenAppPreferences {
    override val state: Flow<HiddenAppPreferenceState> = dataStore.data
        .map(::mapPreferences)
        .catch { emit(HiddenAppPreferenceState()) }

    override suspend fun setLayoutMode(mode: HiddenAppLayoutMode) {
        dataStore.edit { it[LAYOUT_MODE] = mode.name }
    }

    override suspend fun setSortMode(mode: HiddenAppSortMode) {
        dataStore.edit { it[SORT_MODE] = mode.name }
    }

    override suspend fun dismissUsageNotice() {
        dataStore.edit { it[NOTICE_DISMISSED] = true }
    }

    override suspend fun markBatchTipSeen() {
        dataStore.edit { it[BATCH_TIP_SEEN] = true }
    }

    override suspend fun markSortTipSeen() {
        dataStore.edit { it[SORT_TIP_SEEN] = true }
    }

    private fun mapPreferences(preferences: Preferences) = HiddenAppPreferenceState(
        layoutMode = preferences[LAYOUT_MODE]
            ?.let { runCatching { HiddenAppLayoutMode.valueOf(it) }.getOrNull() }
            ?: HiddenAppLayoutMode.Grid,
        sortMode = preferences[SORT_MODE]
            ?.let { runCatching { HiddenAppSortMode.valueOf(it) }.getOrNull() }
            ?: HiddenAppSortMode.Manual,
        usageNoticeDismissed = preferences[NOTICE_DISMISSED] ?: false,
        hasSeenBatchTip = preferences[BATCH_TIP_SEEN] ?: false,
        hasSeenSortTip = preferences[SORT_TIP_SEEN] ?: false,
    )

    private companion object {
        val LAYOUT_MODE = stringPreferencesKey("hidden_app_layout_mode")
        val SORT_MODE = stringPreferencesKey("hidden_app_sort_mode")
        val NOTICE_DISMISSED = booleanPreferencesKey("hidden_apps_notice_dismissed")
        val BATCH_TIP_SEEN = booleanPreferencesKey("has_seen_hidden_apps_batch_tip")
        val SORT_TIP_SEEN = booleanPreferencesKey("has_seen_hidden_apps_sort_tip")
    }
}
