package com.aurora.calculatorvault.app

import android.app.Application
import com.aurora.calculatorvault.core.datastore.PreferencesDataSource
import com.aurora.calculatorvault.core.datastore.vaultPreferencesDataStore

class CalculatorVaultApp : Application() {
    val preferencesDataSource: PreferencesDataSource by lazy {
        PreferencesDataSource(vaultPreferencesDataStore)
    }
}

