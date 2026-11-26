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
import com.aurora.calculatorvault.core.guide.GuideType
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
import com.aurora.calculatorvault.feature.legal.data.LocalInformationRecords
import com.aurora.calculatorvault.feature.legal.data.SdkProcessingIndicators
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
import kotlinx.coroutines.withContext
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

    private val analyticsInitializerDelegate = lazy {
        AnalyticsInitializer(
            context = applicationContext,
            consent = privacyConsent,
            tracker = analyticsTracker,
        )
    }
    private val analyticsInitializer by analyticsInitializerDelegate

    private val buglyInitializerDelegate = lazy {
        BuglyInitializer(
            context = applicationContext,
            consent = privacyConsent,
        )
    }
    private val buglyInitializer by buglyInitializerDelegate

    internal fun readSdkProcessingIndicators(): SdkProcessingIndicators = SdkProcessingIndicators(
        umengInitialized = analyticsInitializerDelegate.isInitialized() && analyticsInitializer.isInitialized(),
        buglyInitialized = buglyInitializerDelegate.isInitialized() && buglyInitializer.isInitialized(),
        wechatRegistered = wechatSdkDelegate.isInitialized() && wechatSdk.isRegistered(),
    )

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

    private var privacyFeaturesStarted = false

    suspend fun onPrivacyAccepted() {
        withContext(Dispatchers.Main.immediate) {
            if (!hasPrivacyConsent() || privacyFeaturesStarted) return@withContext
            privacyFeaturesStarted = true
            startPrivacyFeatures()
            thirdPartySdkInitializer.initializeIfAllowed()
            if (privacyConsent.isAllowedNow()) {
                accountSession.restore()
                adminSession.revalidate()
                deviceTrial.sync()
                appLockProtectionCoordinator.reconcile()
            }
        }
    }

    val privacyWithdrawalController by lazy {
        PrivacyWithdrawalController(
            scope = applicationScope,
            persistAndStopSdks = {
                privacyWithdrawn = true
                thirdPartySdkInitializer.withdrawConsent {
                    securityPreferencesDataSource.withdrawPrivacy()
                }
            },
            endSessions = {
                wechatAuthorization.cancel(privacyWithdrawn = true)
                accountSession.logout()
                adminSession.clearLocal()
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

    internal suspend fun readLocalInformationRecords(): LocalInformationRecords? {
        if (!privacyConsent.isAllowedNow()) return null
        return database.collectedInformationDao().readLocalRecords()
            .takeIf { privacyConsent.isAllowedNow() }
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

    internal fun acknowledgeGuide(type: GuideType) {
        applicationScope.launch { guidePreferences.markShown(type) }
    }

    val loginPromptPreferences by lazy {
        LoginPromptPreferences(vaultPreferencesDataStore)
    }

    val trialGrantedToastStore by lazy {
        com.aurora.calculatorvault.feature.trial.TrialGrantedToastStore(vaultPreferencesDataStore)
    }

    val deviceTrial by lazy {
        com.aurora.calculatorvault.feature.trial.DeviceTrialRepository(
            com.aurora.calculatorvault.feature.trial.EncryptedTrialStore(applicationContext),
            com.aurora.calculatorvault.feature.trial.AndroidTrialClock(applicationContext),
            com.aurora.calculatorvault.feature.trial.AndroidDeviceIdentityProvider(applicationContext, privacyConsent),
            HttpAuthApi(BuildConfig.TRIAL_API_BASE_URL, BuildConfig.DEBUG, localHttpBaseUrl = BuildConfig.TRIAL_HTTP_BASE_URL, trialOnlyBaseUrl = BuildConfig.TRIAL_HTTP_BASE_URL), applicationScope, ::hasPrivacyConsent,
            { BuildConfig.TRIAL_DEVICE_UPLOAD_ENABLED })
    }

    val accountSession: AccountSessionRepository by lazy {
        AccountSessionRepository(HttpAuthApi(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, localHttpBaseUrl = BuildConfig.LOCAL_HTTP_BASE_URL),
            KeystoreTokenStore(applicationContext), applicationScope, ::hasPrivacyConsent,
            serverIssuedInstallAllowed = BuildConfig.DEBUG && BuildConfig.TEST_SESSION_INSTALL_ENABLED,
            // Logging out ends that account's local purchase context; server orders and VIP are untouched.
            // Does not clear AdminSession (WeChat logout ≠ admin logout).
            onLoggedOut = { userId -> paymentRepository.discardPending(userId) })
    }

    val adminSession: com.aurora.calculatorvault.feature.admin.AdminSessionRepository by lazy {
        com.aurora.calculatorvault.feature.admin.AdminSessionRepository(
            api = com.aurora.calculatorvault.feature.admin.HttpAdminAuthApi(
                baseUrl = BuildConfig.API_BASE_URL,
                debug = BuildConfig.DEBUG,
                localHttpBaseUrl = BuildConfig.LOCAL_HTTP_BASE_URL,
            ),
            store = com.aurora.calculatorvault.feature.admin.KeystoreAdminSessionStore(applicationContext),
            scope = applicationScope,
            privacyAllowed = ::hasPrivacyConsent,
        )
    }

    private val wechatSdkDelegate = lazy { AndroidWeChatSdkBridge(applicationContext, BuildConfig.WECHAT_APP_ID, privacyConsent) }
    val wechatSdk by wechatSdkDelegate

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
     * JVM unit tests inject a test-source-set Fake provider directly; androidTest overrides
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
            adminSession = adminSession.state,
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

    /** VIP purchase data (plans / orders) against the same server as login and membership. */
    val paymentRepository: com.aurora.calculatorvault.feature.payment.data.PaymentRepository by lazy {
        com.aurora.calculatorvault.feature.payment.data.PaymentRepository(
            api = com.aurora.calculatorvault.feature.payment.data.HttpPaymentApi(
                baseUrl = BuildConfig.API_BASE_URL,
                debug = BuildConfig.DEBUG,
                localHttpBaseUrl = BuildConfig.LOCAL_HTTP_BASE_URL,
            ),
            store = com.aurora.calculatorvault.feature.payment.data.SharedPreferencesPendingPurchaseStore(applicationContext),
            session = object : com.aurora.calculatorvault.feature.payment.data.PaymentSessionPort {
                override fun currentOwner(requireAuthenticated: Boolean): com.aurora.calculatorvault.feature.payment.data.PurchaseOwner? {
                    val session = accountSession.state.value
                    if (requireAuthenticated && session !is AccountSession.Authenticated) return null
                    val userId = session.entitlementAccount?.userId ?: return null
                    return com.aurora.calculatorvault.feature.payment.data.PurchaseOwner(
                        userId,
                        entitlementRepository.currentAccountGeneration(),
                    )
                }

                override fun accessToken(): String? = accountSession.peekAccessToken()

                override fun onUnauthorized(userId: Long) = accountSession.notifyRemoteSessionInvalid(userId)
            },
            adminPurchaseBlocked = { adminSession.isActive() },
        )
    }

    val vipPurchaseDependencies by lazy {
        com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseDependencies(
            repository = paymentRepository,
            sessionState = accountSession.state,
            entitlementState = entitlementRepository.state,
            membership = object : com.aurora.calculatorvault.feature.membership.presentation.PurchaseMembershipPort {
                override val revision = serverMembershipProvider.membershipStateRevision
                override val syncState = membershipRefreshCoordinator.syncState
                override fun requestRefreshAfterPurchase() = membershipRefreshCoordinator.refreshAfterPurchase()
                override fun hasVerifiedVip(userId: Long) =
                    serverMembershipProvider.membershipFor(userId, entitlementRepository.currentAccountGeneration()) is
                        com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult.VerifiedActive
            },
            payLaunchPolicy = com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy(
                allowTestKind = BuildConfig.PAYMENT_TEST_KIND_ALLOWED,
                cashierHosts = com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
                    .parseHosts(BuildConfig.PAYMENT_CASHIER_HOSTS),
            ),
            elapsedRealtime = { SystemClock.elapsedRealtime() },
            adminPurchaseBlocked = { adminSession.isActive() },
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
            privacyAllowedNow = privacyConsent::isAllowedNow,
            usageAccessGranted = {
                UsageAccessPermissionHelper(applicationContext, consent = privacyConsent).hasUsageAccess()
            },
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(vaultSessionManager))
        applicationScope.launch {
            // Also covers returning with a previously accepted current policy.
            onPrivacyAccepted()
        }
    }

    private fun startPrivacyFeatures() {
        deviceTrial.initialize()
        deviceTrial.startTicker()
        try {
            val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
            connectivity.registerDefaultNetworkCallback(
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        applicationScope.launch {
                            if (!privacyConsent.isAllowedNow()) return@launch
                            deviceTrial.sync()
                            paymentRepository.onNetworkAvailable()
                        }
                    }

                    override fun onLost(network: android.net.Network) {
                        applicationScope.launch {
                            if (!privacyConsent.isAllowedNow()) return@launch
                            // A replacement default network may already be active; only report a real loss.
                            val active = connectivity.activeNetwork
                            if (active == null || active == network) paymentRepository.onNetworkLost()
                        }
                    }
                })
            if (connectivity.activeNetwork == null) paymentRepository.onNetworkLost()
        } catch (_: Exception) { /* Foreground and consent events still synchronize. */ }
        applicationScope.launch {
            vaultPreferencesDataStore.data.catch { }.collect {
                if (hasPrivacyConsent()) deviceTrial.sync()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                if (!privacyConsent.isAllowedNow()) return
                accountSession.revalidate()
                deviceTrial.refresh()
                deviceTrial.sync()
                membershipRefreshCoordinator.onForeground()
                appLockProtectionCoordinator.onProcessForeground()
            }
        })
        appLockProtectionCoordinator.start()
        membershipRefreshCoordinator.start()
    }

    private companion object {
        const val DATABASE_NAME = "calculator_vault.db"
    }
}
