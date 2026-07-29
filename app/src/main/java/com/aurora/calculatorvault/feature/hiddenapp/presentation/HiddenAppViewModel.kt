package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.data.DefaultHiddenAppPreferences
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppPreferences
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppLayoutMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppSortMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HiddenAppViewModel(
    private val repository: HiddenAppRepositoryContract,
    private val launchHiddenApp: LaunchHiddenAppUseCase,
    private val preferences: HiddenAppPreferences = DefaultHiddenAppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HiddenAppUiState())
    val uiState: StateFlow<HiddenAppUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HiddenAppEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeApps()
        observeRecentApps()
        observePreferences()
        repository.refreshAppAvailability()
    }

    fun updateQuery(query: String) {
        if (_uiState.value.isManualSortMode) return
        _uiState.update { it.copy(query = query) }
    }

    fun clearQuery() = updateQuery("")

    fun toggleLayout() {
        val state = _uiState.value
        if (state.isManualSortMode) return
        val mode = if (state.layoutMode == HiddenAppLayoutMode.Grid) {
            HiddenAppLayoutMode.List
        } else {
            HiddenAppLayoutMode.Grid
        }
        _uiState.update { it.copy(layoutMode = mode) }
        persistPreference { preferences.setLayoutMode(mode) }
    }

    fun openSortDialog() {
        val state = _uiState.value
        if (!state.isBatchMode && !state.isManualSortMode) {
            _uiState.update { it.copy(showSortDialog = true) }
        }
    }

    fun closeSortDialog() {
        _uiState.update { it.copy(showSortDialog = false) }
    }

    fun changeSortMode(mode: HiddenAppSortMode) {
        val state = _uiState.value
        if (state.isBatchMode || state.isManualSortMode) return
        _uiState.update { it.copy(sortMode = mode, showSortDialog = false) }
        persistPreference { preferences.setSortMode(mode) }
    }

    fun dismissUsageNotice() {
        _uiState.update { it.copy(showUsageNotice = false) }
        persistPreference { preferences.dismissUsageNotice() }
    }

    fun showUsageNotice() {
        if (!_uiState.value.isManualSortMode && !_uiState.value.isBatchMode) {
            _uiState.update { it.copy(showUsageNotice = true) }
        }
    }

    fun enterBatchMode() {
        val state = _uiState.value
        if (state.isManualSortMode || state.apps.isEmpty()) return
        _uiState.update {
            it.copy(
                isBatchMode = true,
                selectedPackages = emptySet(),
                showSortDialog = false,
                selectedDetailApp = null,
            )
        }
        viewModelScope.launch { runCatching { preferences.markBatchTipSeen() } }
    }

    fun exitBatchMode() {
        _uiState.update {
            it.copy(
                isBatchMode = false,
                selectedPackages = emptySet(),
                pendingBatchRemoval = false,
            )
        }
    }

    fun toggleSelection(packageName: String) {
        if (!_uiState.value.isBatchMode || _uiState.value.isBatchRemoving) return
        _uiState.update { state ->
            val selection = state.selectedPackages.toMutableSet()
            if (!selection.add(packageName)) selection.remove(packageName)
            state.copy(selectedPackages = selection)
        }
    }

    fun toggleSelectAllVisible() {
        val state = _uiState.value
        if (!state.isBatchMode || state.visibleApps.isEmpty()) return
        val visible = state.visibleApps.mapTo(mutableSetOf(), HiddenApp::packageName)
        _uiState.update {
            it.copy(
                selectedPackages = if (state.areAllVisibleSelected) {
                    it.selectedPackages - visible
                } else {
                    it.selectedPackages + visible
                },
            )
        }
    }

    fun selectInvalidApps() {
        val state = _uiState.value
        if (!state.isBatchMode) return
        if (state.invalidVisiblePackages.isEmpty()) {
            viewModelScope.launch { _effects.send(HiddenAppEffect.NoInvalidApps) }
        } else {
            _uiState.update {
                it.copy(selectedPackages = it.selectedPackages + state.invalidVisiblePackages)
            }
        }
    }

    fun requestBatchRemoval() {
        if (_uiState.value.selectedPackages.isNotEmpty() && !_uiState.value.isBatchRemoving) {
            _uiState.update { it.copy(pendingBatchRemoval = true) }
        }
    }

    fun cancelBatchRemoval() {
        _uiState.update { it.copy(pendingBatchRemoval = false) }
    }

    fun confirmBatchRemoval() {
        val packages = _uiState.value.selectedPackages.toList()
        if (packages.isEmpty() || _uiState.value.isBatchRemoving) return
        _uiState.update { it.copy(isBatchRemoving = true, error = null) }
        viewModelScope.launch {
            try {
                val removed = repository.removeApps(packages)
                if (removed <= 0) error("No rows removed")
                _uiState.update {
                    it.copy(
                        isBatchRemoving = false,
                        pendingBatchRemoval = false,
                        isBatchMode = false,
                        selectedPackages = emptySet(),
                    )
                }
                _effects.send(HiddenAppEffect.BatchRemoved(removed))
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isBatchRemoving = false,
                        error = HiddenAppError.BatchRemoveFailed,
                    )
                }
                _effects.send(HiddenAppEffect.BatchRemoveFailed)
            }
        }
    }

    fun enterManualSort() {
        val state = _uiState.value
        if (state.isBatchMode || state.apps.isEmpty()) return
        _uiState.update {
            it.copy(
                sortMode = HiddenAppSortMode.Manual,
                isManualSortMode = true,
                pendingManualOrder = state.apps.sortedBy(HiddenApp::sortOrder)
                    .map(HiddenApp::packageName),
                showSortDialog = false,
                selectedDetailApp = null,
                query = "",
            )
        }
        persistPreference { preferences.setSortMode(HiddenAppSortMode.Manual) }
        viewModelScope.launch { runCatching { preferences.markSortTipSeen() } }
    }

    fun moveManualApp(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        if (!state.isManualSortMode ||
            fromIndex !in state.pendingManualOrder.indices ||
            toIndex !in state.pendingManualOrder.indices ||
            fromIndex == toIndex
        ) return
        val reordered = state.pendingManualOrder.toMutableList()
        val item = reordered.removeAt(fromIndex)
        reordered.add(toIndex, item)
        _uiState.update { it.copy(pendingManualOrder = reordered) }
    }

    fun requestCancelManualSort() {
        if (_uiState.value.isManualSortMode) {
            _uiState.update { it.copy(showDiscardManualOrder = true) }
        }
    }

    fun keepManualSortEditing() {
        _uiState.update { it.copy(showDiscardManualOrder = false) }
    }

    fun cancelManualSort() {
        _uiState.update {
            it.copy(
                isManualSortMode = false,
                pendingManualOrder = emptyList(),
                showDiscardManualOrder = false,
            )
        }
    }

    fun saveManualSort() {
        val order = _uiState.value.pendingManualOrder
        if (!_uiState.value.isManualSortMode || order.isEmpty() ||
            _uiState.value.isSavingManualOrder
        ) return
        _uiState.update { it.copy(isSavingManualOrder = true, error = null) }
        viewModelScope.launch {
            try {
                check(repository.updateManualOrder(order))
                _uiState.update {
                    it.copy(
                        isSavingManualOrder = false,
                        isManualSortMode = false,
                        pendingManualOrder = emptyList(),
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingManualOrder = false,
                        error = HiddenAppError.ManualSortFailed,
                    )
                }
                _effects.send(HiddenAppEffect.ManualOrderSaveFailed)
            }
        }
    }

    fun openDetails(app: HiddenApp) {
        val state = _uiState.value
        if (!state.isBatchMode && !state.isManualSortMode) {
            _uiState.update { it.copy(selectedDetailApp = app) }
        }
    }

    fun closeDetails() {
        _uiState.update { it.copy(selectedDetailApp = null) }
    }

    fun openAppSettings(app: HiddenApp) {
        if (app.availability != InstalledAppAvailability.Disabled) return
        viewModelScope.launch {
            if (runCatching { repository.openAppSettings(app.packageName) }.getOrDefault(false)) {
                closeDetails()
            } else {
                _uiState.update { it.copy(error = HiddenAppError.LaunchFailed) }
            }
        }
    }

    fun refreshDetails() {
        repository.refreshAppAvailability()
    }

    fun retryLoad() {
        if (_uiState.value.error == HiddenAppError.LoadFailed) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            observeApps()
        }
        repository.refreshAppAvailability()
    }

    fun launchApp(app: HiddenApp) {
        if (_uiState.value.launchingPackageName != null ||
            _uiState.value.isBatchMode ||
            _uiState.value.isManualSortMode
        ) return
        _uiState.update {
            it.copy(
                launchingPackageName = app.packageName,
                launchErrorApp = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = runCatching { launchHiddenApp(app.packageName) }
                .getOrDefault(AppLaunchResult.Failed)
            if (result == AppLaunchResult.Success) {
                _uiState.update { it.copy(launchingPackageName = null) }
                return@launch
            }
            repository.refreshAppAvailability()
            _uiState.update {
                it.copy(
                    launchingPackageName = null,
                    launchErrorApp = app,
                    error = result.toHiddenAppError(),
                )
            }
        }
    }

    fun dismissLaunchError() {
        _uiState.update { it.copy(launchErrorApp = null, error = null) }
    }

    fun requestClearRecent() {
        if (_uiState.value.recentApps.isNotEmpty() && !_uiState.value.isClearingRecent &&
            !_uiState.value.isBatchMode && !_uiState.value.isManualSortMode
        ) {
            _uiState.update { it.copy(pendingClearRecent = true, error = null) }
        }
    }

    fun cancelClearRecent() {
        _uiState.update { it.copy(pendingClearRecent = false) }
    }

    fun confirmClearRecent() {
        if (!_uiState.value.pendingClearRecent || _uiState.value.isClearingRecent) return
        _uiState.update { it.copy(isClearingRecent = true, error = null) }
        viewModelScope.launch {
            try {
                repository.clearRecentHistory()
                _uiState.update {
                    it.copy(isClearingRecent = false, pendingClearRecent = false)
                }
                _effects.send(HiddenAppEffect.RecentCleared)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isClearingRecent = false, error = HiddenAppError.ClearRecentFailed)
                }
                _effects.send(HiddenAppEffect.ClearRecentFailed)
            }
        }
    }

    fun requestRemoval(app: HiddenApp) {
        if (!_uiState.value.isRemoving && !_uiState.value.isBatchMode &&
            !_uiState.value.isManualSortMode
        ) {
            _uiState.update { it.copy(pendingRemoval = app, error = null) }
        }
    }

    fun cancelRemoval() {
        _uiState.update { it.copy(pendingRemoval = null) }
    }

    fun confirmRemoval() {
        val app = _uiState.value.pendingRemoval ?: return
        if (_uiState.value.isRemoving) return
        _uiState.update { it.copy(isRemoving = true, error = null) }
        viewModelScope.launch {
            try {
                if (!repository.removeApp(app.packageName)) {
                    _uiState.update {
                        it.copy(isRemoving = false, error = HiddenAppError.RemoveFailed)
                    }
                    _effects.send(HiddenAppEffect.RemoveFailed)
                    return@launch
                }
                _uiState.update { it.copy(isRemoving = false, pendingRemoval = null) }
                _effects.send(HiddenAppEffect.Removed)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isRemoving = false, error = HiddenAppError.RemoveFailed)
                }
                _effects.send(HiddenAppEffect.RemoveFailed)
            }
        }
    }

    private fun observeApps() {
        viewModelScope.launch {
            try {
                repository.observeHiddenApps().collect { apps ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            apps = apps,
                            selectedPackages = state.selectedPackages
                                .intersect(apps.mapTo(mutableSetOf(), HiddenApp::packageName)),
                            selectedDetailApp = state.selectedDetailApp?.let { selected ->
                                apps.firstOrNull { it.packageName == selected.packageName }
                            },
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = HiddenAppError.LoadFailed)
                }
            }
        }
    }

    private fun observeRecentApps() {
        viewModelScope.launch {
            try {
                repository.observeRecentApps(RECENT_REPOSITORY_LIMIT).collect { apps ->
                    _uiState.update { it.copy(recentApps = apps.take(RECENT_HOME_LIMIT)) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = HiddenAppError.LoadFailed) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.state.collect { preference ->
                _uiState.update {
                    it.copy(
                        layoutMode = preference.layoutMode,
                        sortMode = if (it.isManualSortMode) {
                            HiddenAppSortMode.Manual
                        } else {
                            preference.sortMode
                        },
                        showUsageNotice = !preference.usageNoticeDismissed,
                    )
                }
            }
        }
    }

    private fun persistPreference(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (runCatching { block() }.isFailure) {
                _effects.send(HiddenAppEffect.PreferenceSaveFailed)
            }
        }
    }

    private fun AppLaunchResult.toHiddenAppError(): HiddenAppError = when (this) {
        AppLaunchResult.NotInstalled -> HiddenAppError.NotInstalled
        AppLaunchResult.Disabled -> HiddenAppError.Disabled
        AppLaunchResult.NoLaunchIntent,
        AppLaunchResult.ActivityNotFound,
        -> HiddenAppError.NoLaunchIntent
        AppLaunchResult.SecurityBlocked -> HiddenAppError.LaunchBlocked
        AppLaunchResult.InvalidPackage,
        AppLaunchResult.Failed,
        AppLaunchResult.Success,
        -> HiddenAppError.LaunchFailed
    }

    class Factory(
        private val repository: HiddenAppRepositoryContract,
        private val launchHiddenApp: LaunchHiddenAppUseCase,
        private val preferences: HiddenAppPreferences = DefaultHiddenAppPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HiddenAppViewModel::class.java))
            return HiddenAppViewModel(repository, launchHiddenApp, preferences) as T
        }
    }

    private companion object {
        const val RECENT_REPOSITORY_LIMIT = 10
        const val RECENT_HOME_LIMIT = 5
    }
}
