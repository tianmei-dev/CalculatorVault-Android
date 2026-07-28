package com.aurora.calculatorvault.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.datastore.vaultPreferencesDataStore
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.core.security.session.AppLifecycleObserver
import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.feature.calculator.domain.StoredVaultPasswordVerifier
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingRepository
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepository

class CalculatorVaultApp : Application() {
    private val securityPreferencesDataSource by lazy {
        DataStoreSecurityPreferencesDataSource(vaultPreferencesDataStore)
    }

    private val passwordHasher by lazy {
        Pbkdf2PasswordHasher()
    }

    val vaultSessionManager = VaultSessionManager()

    val vaultUnlockUseCase by lazy {
        VaultUnlockUseCase(
            StoredVaultPasswordVerifier(
                dataSource = securityPreferencesDataSource,
                passwordHasher = passwordHasher,
            ),
        )
    }

    val onboardingRepository by lazy {
        OnboardingRepository(
            dataSource = securityPreferencesDataSource,
            passwordHasher = passwordHasher,
        )
    }

    val changePasswordRepository by lazy {
        ChangePasswordRepository(
            dataSource = securityPreferencesDataSource,
            passwordHasher = passwordHasher,
        )
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(vaultSessionManager),
        )
    }
}
