package com.aurora.calculatorvault.feature.applock.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.applock.domain.AppLockPermissionTarget
import com.aurora.calculatorvault.feature.applock.domain.AppLockRepository
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.applock.domain.LockableApp
import com.aurora.calculatorvault.feature.applock.domain.OverlayPermissionHelper
import com.aurora.calculatorvault.feature.applock.domain.UsageAccessPermissionHelper
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

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
        _uiState.update {
            it.copy(
                hasUsageAccess = usagePermissionHelper.hasUsageAccess(),
                hasOverlayPermission = overlayPermissionHelper.isGranted(),
                isMonitorRunning = AppLockMonitorService.isRunning,
                isRefreshingPermissions = false,
            )
        }
        syncMonitorState()
    }

    fun dismissPermissionPrompt() {
        _uiState.update { it.copy(pendingPermissionTarget = null) }
    }

    fun toggleLock(app: LockableApp, locked: Boolean) {
        val state = _uiState.value
        if (state.updatingPackage != null) return
        if (locked && !state.hasUsageAccess) {
            _uiState.update { it.copy(pendingPermissionTarget = AppLockPermissionTarget.UsageAccess) }
            return
        }
        if (locked && !state.hasOverlayPermission) {
            _uiState.update { it.copy(pendingPermissionTarget = AppLockPermissionTarget.Overlay) }
            return
        }
        _uiState.update { it.copy(updatingPackage = app.packageName, error = null) }
        viewModelScope.launch {
            when (repository.setLocked(app.packageName, app.appName, locked)) {
                AppLockSetResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            updatingPackage = null,
                            apps = state.apps.map { current ->
                                if (current.packageName == app.packageName) {
                                    current.copy(locked = locked)
                                } else {
                                    current
                                }
                            },
                        )
                    }
                    syncMonitorState()
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

    private fun syncMonitorState() {
        val state = _uiState.value
        if (state.lockedPackages.isNotEmpty() &&
            state.hasUsageAccess &&
            state.hasOverlayPermission
        ) {
            if (!AppLockMonitorService.isRunning) {
                AppLockMonitorService.start(appContext)
            }
        } else if (state.lockedPackages.isEmpty() && AppLockMonitorService.isRunning) {
            AppLockMonitorService.stop(appContext)
        }
        _uiState.update { it.copy(isMonitorRunning = AppLockMonitorService.isRunning) }
    }

    class Factory(
        private val repository: AppLockRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppLockViewModel::class.java))
            return AppLockViewModel(
                repository = repository,
                usagePermissionHelper = UsageAccessPermissionHelper(appContext),
                overlayPermissionHelper = OverlayPermissionHelper(appContext),
                appContext = appContext,
            ) as T
        }
    }
}
