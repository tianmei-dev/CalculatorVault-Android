package com.aurora.calculatorvault.feature.hiddenapp.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppLayoutMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppSortMode
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenAppPreferencesTest {
    @Test
    fun `defaults save restore and unknown values fall back safely`() = runTest {
        val file = File(System.getProperty("java.io.tmpdir"), "hidden-app-${System.nanoTime()}.preferences_pb")
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = DataStoreHiddenAppPreferences(store)

        val defaults = preferences.state.first()
        assertEquals(HiddenAppLayoutMode.Grid, defaults.layoutMode)
        assertEquals(HiddenAppSortMode.Manual, defaults.sortMode)
        assertFalse(defaults.usageNoticeDismissed)

        preferences.setLayoutMode(HiddenAppLayoutMode.List)
        preferences.setSortMode(HiddenAppSortMode.NameDescending)
        preferences.dismissUsageNotice()
        val saved = preferences.state.first()
        assertEquals(HiddenAppLayoutMode.List, saved.layoutMode)
        assertEquals(HiddenAppSortMode.NameDescending, saved.sortMode)
        assertEquals(true, saved.usageNoticeDismissed)

        store.edit {
            it[stringPreferencesKey("hidden_app_layout_mode")] = "UNKNOWN"
            it[stringPreferencesKey("hidden_app_sort_mode")] = "UNKNOWN"
        }
        val recovered = preferences.state.first()
        assertEquals(HiddenAppLayoutMode.Grid, recovered.layoutMode)
        assertEquals(HiddenAppSortMode.Manual, recovered.sortMode)

        scope.cancel()
        file.delete()
    }
}
