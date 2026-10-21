package com.aurora.calculatorvault.feature.login.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.IOException

class LoginPromptPreferences(private val dataStore: DataStore<Preferences>) {
    /** Persist before presenting, so closing or restarting cannot trigger another prompt. */
    suspend fun claimFirstHomePrompt(): Boolean = try {
        var claimed = false
        dataStore.edit { preferences ->
            if (preferences[FirstHomePromptShown] != true) {
                preferences[FirstHomePromptShown] = true
                claimed = true
            }
        }
        claimed
    } catch (_: IOException) {
        // A storage failure must not block access to the local vault.
        false
    }

    private companion object {
        val FirstHomePromptShown = booleanPreferencesKey("login_first_home_prompt_shown")
    }
}
