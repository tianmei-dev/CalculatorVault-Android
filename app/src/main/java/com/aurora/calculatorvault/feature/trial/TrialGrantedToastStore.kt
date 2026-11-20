package com.aurora.calculatorvault.feature.trial

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.IOException

/** One-shot toast after the device first receives a server-confirmed trial. */
class TrialGrantedToastStore(private val dataStore: DataStore<Preferences>) {
    /** Atomically claim the toast slot; returns true only the first time. */
    suspend fun claimShow(): Boolean = try {
        var show = false
        dataStore.edit { prefs ->
            if (prefs[Shown] != true) {
                prefs[Shown] = true
                show = true
            }
        }
        show
    } catch (_: IOException) {
        false
    }

    /** Mark shown without toasting (existing grants before this feature shipped). */
    suspend fun seedShownIfAbsent() {
        try {
            dataStore.edit { prefs ->
                if (prefs[Shown] != true) prefs[Shown] = true
            }
        } catch (_: IOException) {
            // Best-effort; claimShow still prevents repeat toasts once writable.
        }
    }

    private companion object {
        val Shown = booleanPreferencesKey("trial_granted_toast_shown")
    }
}
