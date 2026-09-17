package com.aurora.calculatorvault.feature.vault.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.analytics.AnalyticsEvent
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.guide.GuideType
import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.applock.presentation.AppLockScreen
import com.aurora.calculatorvault.feature.applock.presentation.AppLockViewModel
import com.aurora.calculatorvault.feature.appmanagement.presentation.AppManagementScreen
import com.aurora.calculatorvault.feature.appmanagement.presentation.AppManagementViewModel
import com.aurora.calculatorvault.feature.disguise.presentation.AppDisguiseScreen
import com.aurora.calculatorvault.feature.disguise.presentation.AppDisguiseViewModel
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppScreen
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppPickerScreen
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppPickerViewModel
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppViewModel
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaScreen
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaViewModel
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentScreen
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import com.aurora.calculatorvault.feature.legal.presentation.PrivacyDocumentsScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.GuideTargetShape
import com.aurora.calculatorvault.feature.onboarding.presentation.ProgressiveGuideOverlay
import com.aurora.calculatorvault.feature.settings.presentation.AboutScreen
import com.aurora.calculatorvault.feature.settings.presentation.ContactUsScreen
import com.aurora.calculatorvault.feature.settings.presentation.SettingsScreen
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordScreen
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordStep
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordViewModel
import com.aurora.calculatorvault.feature.settings.presentation.ForgotPasswordScreen
import com.aurora.calculatorvault.ui.component.VaultBottomNavigation
import com.aurora.calculatorvault.ui.component.VaultNavigationItem
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

@Composable
fun VaultMainScreen(
    onExitVault: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CalculatorVaultApp
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val messageController = LocalAppMessageController.current
    val passwordChangedMessage = stringResource(R.string.password_change_success)
    var activeGuide by remember { mutableStateOf<GuideType?>(null) }
    var pendingDisguiseCreatedGuide by remember { mutableStateOf(false) }
    var disguiseTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var appLockTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var privateAlbumTargetBounds by remember { mutableStateOf<Rect?>(null) }
    val tabs = VaultMainTab.entries
    val tabRoutes = tabs.map { it.route.path }.toSet()

    BackHandler(enabled = currentRoute in tabRoutes) {
        onExitVault()
    }

    Scaffold(
        containerColor = AppColors.BackgroundPrimary,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (currentRoute in tabRoutes) {
                VaultBottomNavigation(
                    items = tabs.map { tab ->
                        VaultNavigationItem(
                            label = stringResource(tab.titleRes),
                            icon = tab.icon,
                            selected = currentRoute == tab.route.path,
                            testTag = tab.testTag,
                            onClick = {
                                when (tab) {
                                    VaultMainTab.AppLock -> application.analyticsTracker.track(
                                        AnalyticsEvent.AppLockClick,
                                    )
                                    VaultMainTab.PrivateAlbum -> application.analyticsTracker.track(
                                        AnalyticsEvent.PrivateAlbumClick,
                                    )
                                    else -> Unit
                                }
                                navController.navigate(tab.route.path) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VaultMainTab.default.route.path,
            // Scaffold padding already includes system bars and the IME. Nested
            // screens must not subtract those insets a second time.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(VaultTabRoute.AppManagement.path) {
                val appManagementViewModel: AppManagementViewModel = viewModel(
                    factory = AppManagementViewModel.Factory(
                        application.hiddenAppRepository,
                        application.disguiseEntryRepository,
                    ),
                )
                AppManagementScreen(
                    viewModel = appManagementViewModel,
                    onOpenDisguise = {
                        application.analyticsTracker.track(AnalyticsEvent.AppDisguiseClick)
                        navController.navigate(VaultTabRoute.AppDisguise.path) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPrivateApps = {
                        navController.navigate(VaultTabRoute.HiddenApp.path) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(VaultTabRoute.AppDisguise.path) {
                LaunchedEffect(Unit) {
                    val hasExistingEntries = runCatching {
                        application.guideLegacyUsageRepository.hasDisguiseEntries()
                    }.getOrDefault(true)
                    application.guidePreferences.migrateLegacyUsage(
                        GuideType.APP_DISGUISE_CREATED,
                        hasExistingEntries,
                    )
                    if (application.guidePreferences.claim(GuideType.APP_DISGUISE_INTRO)) {
                        activeGuide = GuideType.APP_DISGUISE_INTRO
                    }
                }
                LaunchedEffect(Unit) {
                    application.guidePreferences.disguiseCreatedPending.collect { pending ->
                        if (pending) pendingDisguiseCreatedGuide = true
                    }
                }
                val appDisguiseViewModel: AppDisguiseViewModel = viewModel(
                    factory = AppDisguiseViewModel.Factory(
                        application.disguiseEntryRepository,
                        application.requestPinShortcutUseCase,
                        application.shortcutSyncManager,
                        application.shortcutRepository,
                    ),
                )
                AppDisguiseScreen(
                    viewModel = appDisguiseViewModel,
                    iconProvider = application.appIconProvider,
                    onBackToManagement = navController::popBackStack,
                    onMessage = messageController::show,
                    onCreateTargetBoundsChanged = { disguiseTargetBounds = it },
                )
            }
            composable(VaultTabRoute.AppLock.path) {
                LaunchedEffect(Unit) {
                    val hasEnabledEntries = runCatching {
                        application.guideLegacyUsageRepository.hasEnabledAppLock()
                    }.getOrDefault(true)
                    if (
                        application.guidePreferences.claimWithLegacyUsage(
                            GuideType.APP_LOCK,
                            hasEnabledEntries,
                        )
                    ) {
                        activeGuide = GuideType.APP_LOCK
                    }
                }
                val appLockViewModel: AppLockViewModel = viewModel(
                    factory = AppLockViewModel.Factory(
                        application.appLockRepository,
                        application.applicationContext,
                        application.analyticsTracker,
                    ),
                )
                AppLockScreen(
                    viewModel = appLockViewModel,
                    iconProvider = application.appIconProvider,
                    onMessage = messageController::show,
                    onGuideTargetBoundsChanged = { appLockTargetBounds = it },
                )
            }
            composable(VaultTabRoute.HiddenApp.path) {
                val hiddenAppViewModel: HiddenAppViewModel = viewModel(
                    factory = HiddenAppViewModel.Factory(
                        application.hiddenAppRepository,
                        application.launchHiddenAppUseCase,
                        application.hiddenAppPreferences,
                    ),
                )
                HiddenAppScreen(
                    viewModel = hiddenAppViewModel,
                    iconProvider = application.appIconProvider,
                    onAddApps = {
                        navController.navigate(VaultTabRoute.HiddenAppPicker.path) {
                            launchSingleTop = true
                        }
                    },
                    onBack = navController::popBackStack,
                    onMessage = messageController::show,
                )
            }
            composable(VaultTabRoute.HiddenAppPicker.path) {
                val pickerViewModel: HiddenAppPickerViewModel = viewModel(
                    factory = HiddenAppPickerViewModel.Factory(application.hiddenAppRepository),
                )
                HiddenAppPickerScreen(
                    viewModel = pickerViewModel,
                    iconProvider = application.appIconProvider,
                    onBack = navController::popBackStack,
                    onCompleted = { count ->
                        navController.popBackStack()
                        messageController.showSuccess(
                            context.getString(R.string.hidden_app_added_success, count),
                        )
                    },
                )
            }
            composable(VaultTabRoute.PrivateMedia.path) {
                LaunchedEffect(Unit) {
                    val hasExistingMedia = runCatching {
                        application.guideLegacyUsageRepository.hasPrivateMedia()
                    }.getOrDefault(true)
                    if (
                        application.guidePreferences.claimWithLegacyUsage(
                            GuideType.PRIVATE_ALBUM,
                            hasExistingMedia,
                        )
                    ) {
                        activeGuide = GuideType.PRIVATE_ALBUM
                    }
                }
                val privateMediaViewModel: PrivateMediaViewModel = viewModel(
                    factory = PrivateMediaViewModel.Factory(application.vaultMediaRepository),
                )
                PrivateMediaScreen(
                    viewModel = privateMediaViewModel,
                    sessionManager = application.vaultSessionManager,
                    systemMediaRemovalManager = application.systemMediaRemovalManager,
                    onImportClick = {
                        application.analyticsTracker.track(AnalyticsEvent.PrivateMediaImportClick)
                    },
                    onCreateFolderClick = {
                        application.analyticsTracker.track(AnalyticsEvent.PrivateFolderCreateClick)
                    },
                    onRestoreClick = {
                        application.analyticsTracker.track(AnalyticsEvent.PrivateMediaRestoreClick)
                    },
                    onGuideTargetBoundsChanged = { privateAlbumTargetBounds = it },
                )
            }
            composable(VaultTabRoute.Settings.path) {
                SettingsScreen(
                    onChangePassword = {
                        application.analyticsTracker.track(AnalyticsEvent.ChangePasswordClick)
                        navController.navigate(VaultTabRoute.ChangePassword.path)
                    },
                    onAbout = {
                        navController.navigate(VaultTabRoute.About.path)
                    },
                    onContactUs = {
                        navController.navigate(VaultTabRoute.ContactUs.path)
                    },
                    onPrivacyDocuments = {
                        application.analyticsTracker.track(AnalyticsEvent.PrivacyDocumentsClick)
                        navController.navigate(VaultTabRoute.PrivacyDocuments.path)
                    },
                    onPermissionSettingsClick = {
                        application.analyticsTracker.track(AnalyticsEvent.PermissionSettingsClick)
                    },
                )
            }
            composable(VaultTabRoute.About.path) {
                AboutScreen(onBack = navController::popBackStack)
            }
            composable(VaultTabRoute.ContactUs.path) {
                ContactUsScreen(onBack = navController::popBackStack)
            }
            composable(VaultTabRoute.PrivacyDocuments.path) {
                PrivacyDocumentsScreen(
                    onBack = navController::popBackStack,
                    onOpenDocument = { type ->
                        navController.navigate(VaultTabRoute.LegalDocument.createRoute(type))
                    },
                )
            }
            composable(
                route = VaultTabRoute.LegalDocument.path,
                arguments = listOf(navArgument(VaultTabRoute.LegalDocument.TYPE_ARG) {
                    type = NavType.StringType
                }),
            ) { entry ->
                val type = LegalDocumentType.fromRouteValue(
                    entry.arguments?.getString(VaultTabRoute.LegalDocument.TYPE_ARG),
                )
                LegalDocumentScreen(
                    type = type,
                    onBack = navController::popBackStack,
                )
            }
            composable(VaultTabRoute.ChangePassword.path) {
                val changePasswordViewModel: ChangePasswordViewModel = viewModel(
                    factory = ChangePasswordViewModel.Factory(
                        application.changePasswordRepository,
                    ),
                )
                val state by changePasswordViewModel.uiState.collectAsState()
                LaunchedEffect(state.step) {
                    if (state.step == ChangePasswordStep.Completed) {
                        changePasswordViewModel.cancelFlow()
                        navController.popBackStack(
                            route = VaultTabRoute.Settings.path,
                            inclusive = false,
                        )
                        messageController.showSuccess(passwordChangedMessage)
                    }
                }
                ChangePasswordScreen(
                    state = state,
                    onDigit = changePasswordViewModel::addDigit,
                    onDelete = changePasswordViewModel::deleteDigit,
                    onSubmit = changePasswordViewModel::submit,
                    onBackStep = changePasswordViewModel::returnToPreviousStep,
                    onExit = {
                        changePasswordViewModel.cancelFlow()
                        navController.popBackStack()
                    },
                    onResetSamePassword = changePasswordViewModel::resetSamePassword,
                    onAcceptSamePassword = changePasswordViewModel::acceptSamePassword,
                )
            }
            composable(VaultTabRoute.ForgotPassword.path) {
                ForgotPasswordScreen(onBack = navController::popBackStack)
            }
        }
    }

    LaunchedEffect(activeGuide, pendingDisguiseCreatedGuide) {
        if (activeGuide == null && pendingDisguiseCreatedGuide) {
            pendingDisguiseCreatedGuide = false
            if (application.guidePreferences.claimPendingDisguiseCreated()) {
                activeGuide = GuideType.APP_DISGUISE_CREATED
            }
        }
    }

    activeGuide?.let { guide ->
        val targetBounds = when (guide) {
            GuideType.APP_DISGUISE_INTRO -> disguiseTargetBounds
            GuideType.APP_LOCK -> appLockTargetBounds
            GuideType.PRIVATE_ALBUM -> privateAlbumTargetBounds
            GuideType.VAULT_ENTRY,
            GuideType.APP_DISGUISE_CREATED,
            -> null
        }
        ProgressiveGuideOverlay(
            type = guide,
            targetBounds = targetBounds,
            targetShape = if (guide == GuideType.APP_DISGUISE_INTRO) {
                GuideTargetShape.Circle
            } else {
                GuideTargetShape.RoundedRectangle
            },
            avoidBottomNavigation = currentRoute in tabRoutes,
            onDismiss = { activeGuide = null },
        )
    }
}
