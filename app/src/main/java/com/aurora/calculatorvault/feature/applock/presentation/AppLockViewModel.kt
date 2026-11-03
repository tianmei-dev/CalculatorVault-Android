package com.aurora.calculatorvault.feature.applock.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.core.analytics.AnalyticsEvent
import com.aurora.calculatorvault.core.analytics.AnalyticsTracker
import com.aurora.calculatorvault.feature.applock.domain.AppLockPermissionTarget
import com.aurora.calculatorvault.feature.applock.domain.AppLockProtectionCoordinator
import com.aurora.calculatorvault.feature.applock.domain.AppLockRepository
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.applock.domain.LockableApp
import com.aurora.calculatorvault.feature.applock.domain.OverlayPermissionHelper
import com.aurora.calculatorvault.feature.applock.domain.UsageAccessPermissionHelper
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.ActionPolicy
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppLockViewModel(
    private val repository: AppLockRepository,
    private val usagePermissionHelper: UsageAccessPermissionHelper,
    private val overlayPermissionHelper: OverlayPermissionHelper,
    private val appContext: Context,
    private val analyticsTracker: AnalyticsTracker,
    private val actionPolicy: ActionPolicy,
    private val protectionCoordinator: AppLockProtectionCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private val notifications = AndroidAppLockNotifications(appContext)
    private val notificationCoordinator = com.aurora.calculatorvault.feature.applock.domain.AppLockNotificationCoordinator(notifications)
    val notificationState = notificationCoordinator.state

    fun dismissNotificationExplanation() = notificationCoordinator.dismissExplanation()
    fun confirmNotificationRequest() = notificationCoordinator.confirmRequest(notifications.snapshot())
    fun notificationSettingsIntent() = notifications.settingsIntent()
    fun onNotificationResult(granted: Boolean) {
        val snapshot = notifications.snapshot()
        notificationCoordinator.onResult(if (snapshot.apiLevel >= 33)
            snapshot.copy(runtimeGranted = granted) else snapshot)
        if (granted) AppLockMonitorService.refreshStatusNotification()
    }

    private val _effects = Channel<AppLockEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refreshPermissions()
        observeLockedPackages()
        loadApps()
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearQuery() = updateQuery("")

    fun retryLoad() = loadApps()

    fun refreshPermissions() {
        val notificationSnapshot = notifications.snapshot()
        val notificationsWereEnabled = notificationState.value.snapshot.notificationsEnabled
        notificationCoordinator.refresh(notificationSnapshot)
        if (!notificationsWereEnabled && notificationSnapshot.notificationsEnabled) {
            AppLockMonitorService.refreshStatusNotification()
        }
        _uiState.update {
            it.copy(
                hasUsageAccess = usagePermissionHelper.hasUsageAccess(),
                hasOverlayPermission = overlayPermissionHelper.isGranted(),
                isMonitorRunning = AppLockMonitorService.isRunning,
                protectionEntitled = actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                    is ActionDecision.Allowed,
                isRefreshingPermissions = false,
            )
        }
        protectionCoordinator.onPermissionsOrPrivacyChanged()
        viewModelScope.launch { syncMonitorState() }
    }

    fun dismissPermissionPrompt() {
        _uiState.update { it.copy(pendingPermissionTarget = null) }
    }

    fun toggleLock(app: LockableApp, locked: Boolean) {
        val state = _uiState.value
        if (state.updatingPackage != null) return
        if (locked) {
            when (actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)) {
                is ActionDecision.Denied -> {
                    viewModelScope.launch { _effects.send(AppLockEffect.NavigateToVip) }
                    return
                }
                ActionDecision.Allowed -> Unit
            }
            if (!state.hasUsageAccess) {
                _uiState.update { it.copy(pendingPermissionTarget = AppLockPermissionTarget.UsageAccess) }
                return
            }
            if (!state.hasOverlayPermission) {
                _uiState.update { it.copy(pendingPermissionTarget = AppLockPermissionTarget.Overlay) }
                return
            }
        }
        _uiState.update { it.copy(updatingPackage = app.packageName, error = null) }
        viewModelScope.launch {
            when (repository.setLocked(app.packageName, app.appName, locked)) {
                AppLockSetResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            updatingPackage = null,
                            apps = current.apps.map { item ->
                                if (item.packageName == app.packageName) {
                                    item.copy(locked = locked)
                                } else {
                                    item
                                }
                            },
                        )
                    }
                    syncMonitorState()
                    if (locked) {
                        notificationCoordinator.onLockEnabled(notifications.snapshot())
                        analyticsTracker.track(AnalyticsEvent.AppLockEnable)
                    }
                }
                AppLockSetResult.Rejected -> {
                    _uiState.update {
                        it.copy(
                            updatingPackage = null,
                            error = AppLockError.PackageRejected,
                        )
                    }
                    _effects.send(AppLockEffect.PackageRejected)
                }
                AppLockSetResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            updatingPackage = null,
                            error = AppLockError.SaveFailed,
                        )
                    }
                    _effects.send(AppLockEffect.SaveFailed)
                }
            }
        }
    }

    private fun observeLockedPackages() {
        viewModelScope.launch {
            repository.observeLockedPackages().collect { packages ->
                _uiState.update { state ->
                    state.copy(
                        lockedPackages = packages,
                        apps = state.apps.map { it.copy(locked = it.packageName in packages) },
                        isMonitorRunning = AppLockMonitorService.isRunning,
                        protectionEntitled = actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                            is ActionDecision.Allowed,
                    )
                }
                syncMonitorState()
            }
        }
    }

    private fun loadApps() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val apps = repository.loadLockableApps()
                val locked = _uiState.value.lockedPackages
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apps = apps.map { app -> app.copy(locked = app.packageName in locked) },
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = AppLockError.LoadFailed)
                }
            }
        }
    }

    private suspend fun syncMonitorState() {
        protectionCoordinator.reconcile()
        _uiState.update {
            it.copy(
                isMonitorRunning = AppLockMonitorService.isRunning,
                protectionEntitled = actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                    is ActionDecision.Allowed,
            )
        }
    }

    class Factory(
        private val repository: AppLockRepository,
        private val appContext: Context,
        private val analyticsTracker: AnalyticsTracker,
        private val actionPolicy: ActionPolicy,
        private val protectionCoordinator: AppLockProtectionCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppLockViewModel::class.java))
            return AppLockViewModel(
                repository = repository,
                usagePermissionHelper = UsageAccessPermissionHelper(appContext),
                overlayPermissionHelper = OverlayPermissionHelper(appContext),
                appContext = appContext,
                analyticsTracker = analyticsTracker,
                actionPolicy = actionPolicy,
                protectionCoordinator = protectionCoordinator,
            ) as T
        }
    }
}
