package com.aurora.calculatorvault.app

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import com.aurora.calculatorvault.core.privacy.PrivacyWithdrawalController
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.aurora.calculatorvault.core.analytics.AnalyticsInitializer
import com.aurora.calculatorvault.core.analytics.UmengAnalyticsTracker
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import com.aurora.calculatorvault.core.datastore.security.DataStoreSecurityPreferencesDataSource
import com.aurora.calculatorvault.core.datastore.vaultPreferencesDataStore
import com.aurora.calculatorvault.core.guide.GuidePreferences
import com.aurora.calculatorvault.core.monitoring.BuglyInitializer
import com.aurora.calculatorvault.core.security.Pbkdf2PasswordHasher
import com.aurora.calculatorvault.core.security.recovery.AndroidKeystorePasswordRecoveryCipher
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryRepository
import com.aurora.calculatorvault.core.security.session.AppLifecycleObserver
import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.core.thirdparty.ThirdPartySdkInitializer
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
import com.aurora.calculatorvault.feature.onboarding.data.GuideLegacyUsageRepository
import com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaRepository
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRemovalManager
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRestoreManager
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaImporter
import com.aurora.calculatorvault.feature.privatemedia.storage.VaultMediaStorage
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import com.aurora.calculatorvault.feature.login.data.LoginPromptPreferences
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.feature.login.data.AndroidWeChatSdkBridge
import com.aurora.calculatorvault.feature.login.data.SharedPreferencesWeChatAuthorizationStore
import com.aurora.calculatorvault.feature.login.session.*
import com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationCoordinator
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipProvider
import com.aurora.calculatorvault.feature.membership.data.HttpMembershipApi
import com.aurora.calculatorvault.feature.membership.data.MembershipRefreshCoordinator
import com.aurora.calculatorvault.feature.membership.data.ServerMembershipProvider
import com.aurora.calculatorvault.feature.applock.domain.AppLockProtectionCoordinator
import com.aurora.calculatorvault.feature.applock.domain.OverlayPermissionHelper
import com.aurora.calculatorvault.feature.applock.domain.UsageAccessPermissionHelper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle

class CalculatorVaultApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val securityPreferencesDataSource by lazy {
        DataStoreSecurityPreferencesDataSource(vaultPreferencesDataStore)
    }

    private val passwordHasher by lazy {
        Pbkdf2PasswordHasher()
    }

    val analyticsTracker by lazy {
        UmengAnalyticsTracker(applicationContext, privacyConsent)
    }

    private val analyticsInitializer by lazy {
        AnalyticsInitializer(
            context = applicationContext,
            consent = privacyConsent,
            tracker = analyticsTracker,
        )
    }

    private val buglyInitializer by lazy {
        BuglyInitializer(
            context = applicationContext,
            consent = privacyConsent,
        )
    }

    val thirdPartySdkInitializer by lazy {
        ThirdPartySdkInitializer(
            analyticsInitializer = analyticsInitializer,
            buglyInitializer = buglyInitializer,
            consent = privacyConsent,
        )
    }

    val privacyConsent by lazy {
        com.aurora.calculatorvault.core.privacy.PrivacyConsentGate { securityPreferencesDataSource.read() }
    }

    @Volatile
    var privacyWithdrawn = false
        private set

    suspend fun hasPrivacyConsent(): Boolean =
        !privacyWithdrawn && privacyConsent.check()

    suspend fun onPrivacyAccepted() {
        if (!hasPrivacyConsent()) return
        thirdPartySdkInitializer.initializeIfAllowed()
        accountSession.restore()
        deviceTrial.sync()
    }

    val privacyWithdrawalController by lazy {
        PrivacyWithdrawalController(
            scope = applicationScope,
            persistAndStopSdks = {
                thirdPartySdkInitializer.withdrawConsent {
                    securityPreferencesDataSource.withdrawPrivacy()
                    privacyWithdrawn = true
                }
            },
            endSessions = {
                wechatAuthorization.cancel(privacyWithdrawn = true)
                accountSession.logout()
                vaultSessionManager.lock()
                appLockProtectionCoordinator.bumpGeneration()
                appLockSessionManager.clearAll()
                AppLockMonitorService.stop(this)
            },
        )
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
            source = AndroidLauncherAppSource(packageManager, includeTemporarilyDisabled = true),
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
            importAllowed = {
                val decision = entitlementRepository.actionPolicy.evaluate(
                    EntitlementCapability.PRIVATE_ALBUM_IMPORT,
                )
                decision is ActionDecision.Allowed
            },
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

    val guidePreferences by lazy {
        GuidePreferences(vaultPreferencesDataStore)
    }

    val loginPromptPreferences by lazy {
        LoginPromptPreferences(vaultPreferencesDataStore)
    }

    val deviceTrial by lazy {
        com.aurora.calculatorvault.feature.trial.DeviceTrialRepository(
            com.aurora.calculatorvault.feature.trial.EncryptedTrialStore(applicationContext),
            com.aurora.calculatorvault.feature.trial.AndroidTrialClock(applicationContext),
            com.aurora.calculatorvault.feature.trial.AndroidDeviceIdentityProvider(applicationContext, privacyConsent),
            HttpAuthApi(BuildConfig.TRIAL_API_BASE_URL, BuildConfig.DEBUG, localHttpBaseUrl = BuildConfig.TRIAL_HTTP_BASE_URL, trialOnlyBaseUrl = BuildConfig.TRIAL_HTTP_BASE_URL), applicationScope, ::hasPrivacyConsent,
            { BuildConfig.TRIAL_DEVICE_UPLOAD_ENABLED })
    }

    val accountSession by lazy {
        AccountSessionRepository(HttpAuthApi(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, localHttpBaseUrl = BuildConfig.LOCAL_HTTP_BASE_URL),
            KeystoreTokenStore(applicationContext), applicationScope, ::hasPrivacyConsent)
    }

    val wechatSdk by lazy { AndroidWeChatSdkBridge(applicationContext, BuildConfig.WECHAT_APP_ID, privacyConsent) }

    val wechatAuthorization by lazy {
        WeChatAuthorizationCoordinator(
            sdk = wechatSdk,
            store = SharedPreferencesWeChatAuthorizationStore(applicationContext),
            serverLogin = accountSession,
            consentAllowed = privacyConsent::isAllowedNow,
        )
    }

    /**
     * Production VIP source: server /me only. Never fabricates membership.
     * JVM unit tests may inject FakeMembershipProvider directly; androidTest overrides
     * [EntitlementRepository.stateOverride] from the test source set only.
     * Debug / localIntegration / Release all use [ServerMembershipProvider] —
     * only server-confirmed ACTIVE grants VIP.
     */
    val serverMembershipProvider: ServerMembershipProvider by lazy {
        ServerMembershipProvider(
            api = HttpMembershipApi(
                baseUrl = BuildConfig.API_BASE_URL,
                debug = BuildConfig.DEBUG,
                localHttpBaseUrl = BuildConfig.LOCAL_HTTP_BASE_URL,
            ),
            scope = applicationScope,
            elapsedRealtime = { SystemClock.elapsedRealtime() },
        )
    }

    val membershipProvider: MembershipProvider by lazy { serverMembershipProvider }

    val entitlementRepository by lazy {
        EntitlementRepository(
            deviceTrial = deviceTrial,
            accountSession = accountSession,
            membershipProvider = membershipProvider,
            elapsedRealtime = { SystemClock.elapsedRealtime() },
            wallClockEpochMillis = { System.currentTimeMillis() },
            scope = applicationScope,
        )
    }

    val membershipRefreshCoordinator by lazy {
        MembershipRefreshCoordinator(
            accountSession = accountSession,
            entitlementRepository = entitlementRepository,
            serverMembership = serverMembershipProvider,
            scope = applicationScope,
            elapsedRealtime = { SystemClock.elapsedRealtime() },
            baseUrlConfigured = { BuildConfig.API_BASE_URL.isNotBlank() },
        )
    }

    val appLockProtectionCoordinator by lazy {
        AppLockProtectionCoordinator(
            entitlementState = entitlementRepository.state,
            actionPolicy = entitlementRepository.actionPolicy,
            observeLockedPackages = { appLockRepository.observeLockedPackages() },
            lockedPackageCount = {
                appLockRepository.observeLockedPackages().first().size
            },
            privacyOk = { hasPrivacyConsent() },
            privacyWithdrawn = { privacyWithdrawn },
            usageAccessGranted = { UsageAccessPermissionHelper(applicationContext).hasUsageAccess() },
            overlayPermissionGranted = { OverlayPermissionHelper(applicationContext).isGranted() },
            processForeground = {
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            },
            isMonitorRunning = { AppLockMonitorService.isRunning },
            startMonitor = { AppLockMonitorService.start(this) },
            stopMonitor = { AppLockMonitorService.stop(this) },
            revokeRuntimeProtection = {
                AppLockMonitorService.hideOverlayAndClearSession(this)
            },
            scope = applicationScope,
        )
    }

    val guideLegacyUsageRepository by lazy {
        GuideLegacyUsageRepository(database)
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
        // The restart helper must never open DataStore/Room or initialize SDKs.
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            getSystemService(ActivityManager::class.java)
                .runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        if (processName != packageName) return
        deviceTrial.initialize()
        deviceTrial.startTicker()
        try {
            getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        applicationScope.launch { deviceTrial.sync() }
                    }
                })
        } catch (_: Exception) { /* Foreground and consent events still synchronize. */ }
        applicationScope.launch {
            vaultPreferencesDataStore.data.catch { }.collect {
                if (hasPrivacyConsent()) deviceTrial.sync()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(vaultSessionManager),
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                accountSession.revalidate()
                deviceTrial.refresh()
                deviceTrial.sync()
                membershipRefreshCoordinator.onForeground()
                appLockProtectionCoordinator.onProcessForeground()
            }
        })
        appLockProtectionCoordinator.start()
        membershipRefreshCoordinator.start()
        applicationScope.launch {
            // Persisted consent is read before either third-party SDK is touched.
            thirdPartySdkInitializer.initializeIfAllowed()
            if (hasPrivacyConsent()) accountSession.restore()
            deviceTrial.sync()
            appLockProtectionCoordinator.reconcile()
        }
    }

    private companion object {
        const val DATABASE_NAME = "calculator_vault.db"
    }
}
