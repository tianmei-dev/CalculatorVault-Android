package com.aurora.calculatorvault.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.datastore.vaultPreferencesDataStore
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.core.security.recovery.AndroidKeystorePasswordRecoveryCipher
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryRepository
import com.aurora.calculatorvault.core.security.session.AppLifecycleObserver
import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.feature.applock.domain.AppLockSessionManager
import com.aurora.calculatorvault.feature.applock.domain.AppLockPackagePolicy
import com.aurora.calculatorvault.feature.applock.data.AppLockRepositoryImpl
import com.aurora.calculatorvault.feature.calculator.domain.StoredVaultPasswordVerifier
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepository
import com.aurora.calculatorvault.feature.disguise.shortcut.AndroidShortcutRepository
import com.aurora.calculatorvault.feature.disguise.shortcut.AndroidPinnedShortcutCreator
import com.aurora.calculatorvault.feature.disguise.shortcut.LaunchDisguisedTargetUseCase
import com.aurora.calculatorvault.feature.disguise.shortcut.RequestPinShortcutUseCase
import com.aurora.calculatorvault.feature.disguise.shortcut.ResolveDisguiseShortcutUseCase
import com.aurora.calculatorvault.feature.disguise.shortcut.ResourceDisguiseShortcutIconFactory
import com.aurora.calculatorvault.feature.disguise.shortcut.ShortcutSyncManager
import com.aurora.calculatorvault.feature.disguise.shortcut.VerifyVaultPasswordUseCase
import com.aurora.calculatorvault.feature.hiddenapp.data.CachedPackageManagerIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepository
import com.aurora.calculatorvault.feature.hiddenapp.data.DataStoreHiddenAppPreferences
import com.aurora.calculatorvault.feature.hiddenapp.data.RoomHiddenAppStore
import com.aurora.calculatorvault.feature.hiddenapp.domain.AndroidLauncherAppSource
import com.aurora.calculatorvault.feature.hiddenapp.domain.AndroidHiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.FilteringInstalledAppScanner
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingRepository
import com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaRepository
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRemovalManager
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRestoreManager
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaImporter
import com.aurora.calculatorvault.feature.privatemedia.storage.VaultMediaStorage
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepository

class CalculatorVaultApp : Application() {
    private val securityPreferencesDataSource by lazy {
        DataStoreSecurityPreferencesDataSource(vaultPreferencesDataStore)
    }

    private val passwordHasher by lazy {
        Pbkdf2PasswordHasher()
    }

    val passwordRecoveryRepository by lazy {
        PasswordRecoveryRepository(
            dataSource = securityPreferencesDataSource,
            cipher = AndroidKeystorePasswordRecoveryCipher(),
        )
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
            CalculatorVaultDatabase.MIGRATION_4_5,
            CalculatorVaultDatabase.MIGRATION_5_6,
            CalculatorVaultDatabase.MIGRATION_6_7,
        ).build()
    }

    private val launcherAppSource by lazy {
        AndroidLauncherAppSource(packageManager)
    }

    private val installedAppScanner by lazy {
        FilteringInstalledAppScanner(
            source = launcherAppSource,
            ownPackageName = packageName,
        )
    }

    val hiddenAppRuntime by lazy {
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

    val shortcutRepository by lazy {
        AndroidShortcutRepository(
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

    val shortcutSyncManager by lazy {
        ShortcutSyncManager(
            shortcutRepository = shortcutRepository,
            runtime = hiddenAppRuntime,
        )
    }

    val hiddenAppPreferences by lazy {
        DataStoreHiddenAppPreferences(vaultPreferencesDataStore)
    }

    val launchHiddenAppUseCase by lazy {
        LaunchHiddenAppUseCase(
            runtime = hiddenAppRuntime,
            repository = hiddenAppRepository,
            appLockRepository = appLockRepository,
            appLockSessionManager = appLockSessionManager,
            vaultSessionManager = vaultSessionManager,
        )
    }

    val resolveDisguiseShortcutUseCase by lazy {
        ResolveDisguiseShortcutUseCase(
            repository = disguiseEntryRepository,
            runtime = hiddenAppRuntime,
        )
    }

    val verifyVaultPasswordUseCase by lazy {
        VerifyVaultPasswordUseCase(vaultUnlockUseCase)
    }

    val launchDisguisedTargetUseCase by lazy {
        LaunchDisguisedTargetUseCase(
            repository = disguiseEntryRepository,
            runtime = hiddenAppRuntime,
            launchHiddenAppUseCase = launchHiddenAppUseCase,
            appLockSessionManager = appLockSessionManager,
            appLockRepository = appLockRepository,
        )
    }

    val appIconProvider by lazy {
        CachedPackageManagerIconProvider(packageManager)
    }

    val vaultSessionManager = VaultSessionManager()

    val appLockSessionManager = AppLockSessionManager()

    private val appLockPackagePolicy by lazy {
        AppLockPackagePolicy(
            packageManager = packageManager,
            ownPackageName = packageName,
        )
    }

    val appLockRepository by lazy {
        AppLockRepositoryImpl(
            dao = database.appLockDao(),
            launcherAppSource = launcherAppSource,
            packagePolicy = appLockPackagePolicy,
        )
    }

    private val vaultMediaStorage by lazy {
        VaultMediaStorage(applicationContext)
    }

    private val vaultMediaImporter by lazy {
        VaultMediaImporter(
            contentResolver = contentResolver,
            storage = vaultMediaStorage,
        )
    }

    private val systemMediaRestoreManager by lazy {
        SystemMediaRestoreManager(contentResolver)
    }

    val vaultMediaRepository by lazy {
        VaultMediaRepository(
            albumDao = database.vaultAlbumDao(),
            mediaDao = database.vaultMediaDao(),
            importer = vaultMediaImporter,
            storage = vaultMediaStorage,
            restoreManager = systemMediaRestoreManager,
        )
    }

    val systemMediaRemovalManager by lazy {
        SystemMediaRemovalManager(contentResolver)
    }

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
            passwordRecoveryRepository = passwordRecoveryRepository,
        )
    }

    val changePasswordRepository by lazy {
        ChangePasswordRepository(
            dataSource = securityPreferencesDataSource,
            passwordHasher = passwordHasher,
            passwordRecoveryRepository = passwordRecoveryRepository,
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
