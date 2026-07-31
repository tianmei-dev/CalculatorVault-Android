package com.aurora.calculatorvault.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.datastore.vaultPreferencesDataStore
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.core.security.session.AppLifecycleObserver
import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.feature.calculator.domain.StoredVaultPasswordVerifier
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepository
import com.aurora.calculatorvault.feature.disguise.shortcut.AndroidPinnedShortcutCreator
import com.aurora.calculatorvault.feature.disguise.shortcut.RequestPinShortcutUseCase
import com.aurora.calculatorvault.feature.disguise.shortcut.ResourceDisguiseShortcutIconFactory
import com.aurora.calculatorvault.feature.hiddenapp.data.CachedPackageManagerIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepository
import com.aurora.calculatorvault.feature.hiddenapp.data.DataStoreHiddenAppPreferences
import com.aurora.calculatorvault.feature.hiddenapp.data.RoomHiddenAppStore
import com.aurora.calculatorvault.feature.hiddenapp.domain.AndroidLauncherAppSource
import com.aurora.calculatorvault.feature.hiddenapp.domain.AndroidHiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.FilteringInstalledAppScanner
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingRepository
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepository

class CalculatorVaultApp : Application() {
    private val securityPreferencesDataSource by lazy {
        DataStoreSecurityPreferencesDataSource(vaultPreferencesDataStore)
    }

    private val passwordHasher by lazy {
        Pbkdf2PasswordHasher()
    }

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            CalculatorVaultDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(
            CalculatorVaultDatabase.MIGRATION_1_2,
            CalculatorVaultDatabase.MIGRATION_2_3,
            CalculatorVaultDatabase.MIGRATION_3_4,
        ).build()
    }

    private val installedAppScanner by lazy {
        FilteringInstalledAppScanner(
            source = AndroidLauncherAppSource(packageManager),
            ownPackageName = packageName,
        )
    }

    private val hiddenAppRuntime by lazy {
        AndroidHiddenAppRuntime(
            context = applicationContext,
            packageManager = packageManager,
            ownPackageName = packageName,
        )
    }

    val hiddenAppRepository by lazy {
        HiddenAppRepository(
            scanner = installedAppScanner,
            runtime = hiddenAppRuntime,
            store = RoomHiddenAppStore(database),
        )
    }

    val disguiseEntryRepository by lazy {
        DisguiseEntryRepository(
            database = database,
            scanner = installedAppScanner,
        )
    }

    private val pinnedShortcutCreator by lazy {
        AndroidPinnedShortcutCreator(
            context = applicationContext,
            iconFactory = ResourceDisguiseShortcutIconFactory(applicationContext),
        )
    }

    val requestPinShortcutUseCase by lazy {
        RequestPinShortcutUseCase(
            repository = disguiseEntryRepository,
            creator = pinnedShortcutCreator,
        )
    }

    val hiddenAppPreferences by lazy {
        DataStoreHiddenAppPreferences(vaultPreferencesDataStore)
    }

    val launchHiddenAppUseCase by lazy {
        LaunchHiddenAppUseCase(
            runtime = hiddenAppRuntime,
            repository = hiddenAppRepository,
        )
    }

    val appIconProvider by lazy {
        CachedPackageManagerIconProvider(packageManager)
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

    private companion object {
        const val DATABASE_NAME = "calculator_vault.db"
    }
}
