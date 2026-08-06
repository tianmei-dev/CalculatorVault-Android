package com.aurora.calculatorvault.feature.disguise.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseNamePolicy
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseSortMode
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutStatus
import com.aurora.calculatorvault.feature.disguise.shortcut.PinShortcutRequestResult
import com.aurora.calculatorvault.feature.disguise.shortcut.RequestPinShortcutUseCase
import com.aurora.calculatorvault.feature.disguise.shortcut.ShortcutOperationResult
import com.aurora.calculatorvault.feature.disguise.shortcut.ShortcutRepository
import com.aurora.calculatorvault.feature.disguise.shortcut.ShortcutSyncManager
import com.aurora.calculatorvault.feature.disguise.shortcut.ShortcutUpdateRequest
import com.aurora.calculatorvault.feature.disguise.shortcut.SyncedDisguiseEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class AppDisguiseViewModel(
    private val repository: DisguiseEntryRepositoryContract,
    private val requestPinShortcutUseCase: RequestPinShortcutUseCase? = null,
    private val shortcutSyncManager: ShortcutSyncManager? = null,
    private val shortcutRepository: ShortcutRepository? = null,
    private val collator: Collator = Collator.getInstance(Locale.getDefault()),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppDisguiseUiState())
    val uiState: StateFlow<AppDisguiseUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AppDisguiseEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeEntries()
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, error = AppDisguiseError.LoadFailed)
                    }
                }
                .collect { entries ->
                    val syncedEntries = syncEntries(entries)
                    _uiState.update { state ->
                        state.copy(
                            entries = entries,
                            syncedEntries = syncedEntries,
                            visibleEntries = filterAndSort(syncedEntries, state.query, state.sortMode),
                            isLoading = false,
                            selectedDetails = state.selectedDetails?.let { selected ->
                                entries.firstOrNull { it.id == selected.id } ?: selected
                            },
                            savedEntry = state.savedEntry?.let { saved ->
                                entries.firstOrNull { it.id == saved.id } ?: saved
                            },
                            error = null,
                        )
                    }
                }
        }
    }

    fun updateQuery(value: String) {
        _uiState.update { state ->
            state.copy(
                query = value,
                visibleEntries = filterAndSort(
                    state.syncedEntries,
                    value,
                    state.sortMode,
                ),
            )
        }
    }

    fun setSortMode(mode: DisguiseSortMode) {
        _uiState.update { state ->
            state.copy(
                sortMode = mode,
                visibleEntries = filterAndSort(state.syncedEntries, state.query, mode),
            )
        }
    }

    fun refreshShortcutStatus() {
        val manager = shortcutSyncManager ?: return
        if (_uiState.value.isSyncingShortcuts) return
        _uiState.update { it.copy(isSyncingShortcuts = true) }
        viewModelScope.launch {
            val entries = _uiState.value.entries
            val synced = runCatching { manager.sync(entries) }
                .getOrElse { entries.map { SyncedDisguiseEntry(it, fallbackShortcutStatus(it)) } }
            _uiState.update { state ->
                state.copy(
                    visibleEntries = filterAndSort(synced, state.query, state.sortMode),
                    syncedEntries = synced,
                    isSyncingShortcuts = false,
                )
            }
        }
    }

    fun startCreate() {
        _uiState.update {
            it.copy(
                page = AppDisguisePage.SelectApp,
                editingId = null,
                selectedApp = null,
                customName = "",
                selectedIcon = DisguiseIconId.Files,
                appQuery = "",
                error = null,
            )
        }
        scanApps()
    }

    fun updateAppQuery(value: String) {
        _uiState.update { state ->
            state.copy(
                appQuery = value,
                visibleInstalledApps = filterApps(state.installedApps, value),
            )
        }
    }

    fun retryScan() = scanApps()

    fun selectApp(app: com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp) {
        _uiState.update { state ->
            state.copy(
                page = AppDisguisePage.SetName,
                selectedApp = app,
                customName = if (state.editingId == null) {
                    DisguiseNamePolicy.normalize(app.appName)
                } else {
                    state.customName
                },
                error = null,
            )
        }
    }

    fun updateCustomName(value: String) {
        _uiState.update {
            it.copy(customName = DisguiseNamePolicy.normalize(value), error = null)
        }
    }

    fun continueFromName() {
        if (DisguiseNamePolicy.isValid(_uiState.value.customName)) {
            _uiState.update { it.copy(page = AppDisguisePage.SelectIcon) }
        }
    }

    fun selectIcon(iconId: DisguiseIconId) {
        _uiState.update { it.copy(selectedIcon = iconId) }
    }

    fun continueToPreview() {
        if (_uiState.value.selectedApp != null) {
            _uiState.update { it.copy(page = AppDisguisePage.Preview) }
        }
    }

    fun save() {
        val state = _uiState.value
        val app = state.selectedApp ?: return
        if (state.isSaving || !DisguiseNamePolicy.isValid(state.customName)) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                if (state.editingId == null) {
                    val id = repository.create(
                        packageName = app.packageName,
                        targetAppName = app.appName,
                        customName = state.customName.trim(),
                        iconId = state.selectedIcon,
                    )
                    _uiState.update {
                        it.copy(
                            page = AppDisguisePage.Saved,
                            isSaving = false,
                            savedEntry = DisguiseEntry(
                                id = id,
                                packageName = app.packageName,
                                targetAppName = app.appName,
                                customName = state.customName.trim(),
                                iconId = state.selectedIcon,
                                createdAt = 0,
                                updatedAt = 0,
                            ),
                        )
                    }
                    _effects.send(AppDisguiseEffect.Saved)
                } else {
                    val oldEntry = repository.findById(state.editingId)
                    val success = repository.update(
                        id = state.editingId,
                        packageName = app.packageName,
                        targetAppName = app.appName,
                        customName = state.customName.trim(),
                        iconId = state.selectedIcon,
                    )
                    if (!success) error("entry not found")
                    val shortcutUpdateResult = oldEntry?.shortcutId?.let { shortcutId ->
                        shortcutRepository?.update(
                            ShortcutUpdateRequest(
                                shortcutId = shortcutId,
                                displayName = state.customName.trim(),
                                iconId = state.selectedIcon,
                            ),
                        )
                    }
                    resetToList()
                    _effects.send(AppDisguiseEffect.Updated)
                    if (
                        shortcutUpdateResult != null &&
                        shortcutUpdateResult != ShortcutOperationResult.Success &&
                        shortcutUpdateResult != ShortcutOperationResult.NotFound
                    ) {
                        _effects.send(AppDisguiseEffect.ShortcutRequestFailed)
                    } else if (shortcutUpdateResult == ShortcutOperationResult.Success) {
                        _effects.send(AppDisguiseEffect.ShortcutUpdated)
                    }
                    refreshShortcutStatus()
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = AppDisguiseError.SaveFailed)
                }
            }
        }
    }

    fun edit(entry: DisguiseEntry) {
        _uiState.update {
            it.copy(
                page = AppDisguisePage.SelectApp,
                editingId = entry.id,
                selectedApp = com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp(
                    packageName = entry.packageName,
                    appName = entry.targetAppName,
                ),
                customName = entry.customName,
                selectedIcon = entry.iconId,
                error = null,
            )
        }
        scanApps()
    }

    fun showDetails(entry: DisguiseEntry) {
        _uiState.update {
            it.copy(page = AppDisguisePage.Details, selectedDetails = entry)
        }
    }

    fun requestDelete(entry: DisguiseEntry) {
        _uiState.update { it.copy(pendingDelete = entry) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val entry = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            try {
                val shortcutId = entry.shortcutId
                if (!repository.delete(entry.id)) error("entry not found")
                val removeResult = shortcutId?.let { shortcutRepository?.remove(it) }
                resetToList()
                _effects.send(AppDisguiseEffect.Deleted)
                when (removeResult) {
                    ShortcutOperationResult.ManualRemovalRequired -> {
                        _uiState.update { it.copy(showManualDeleteDialog = true) }
                        _effects.send(AppDisguiseEffect.ManualShortcutRemovalRequired)
                    }
                    ShortcutOperationResult.SecurityBlocked,
                    ShortcutOperationResult.Failed,
                    -> _effects.send(AppDisguiseEffect.ShortcutRequestFailed)
                    ShortcutOperationResult.Success,
                    ShortcutOperationResult.NotFound,
                    null,
                    -> Unit
                    ShortcutOperationResult.Unsupported,
                    ShortcutOperationResult.IconGenerationFailed,
                    -> Unit
                }
                refreshShortcutStatus()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(pendingDelete = null, error = AppDisguiseError.DeleteFailed)
                }
            }
        }
    }

    fun requestShortcut(entry: DisguiseEntry) {
        if (_uiState.value.requestingShortcutEntryId != null) return
        if (
            entry.shortcutRequestState == ShortcutRequestState.RequestSubmitted ||
            entry.shortcutRequestState == ShortcutRequestState.LauncherAccepted
        ) {
            _uiState.update { it.copy(pendingDuplicateRequest = entry) }
        } else {
            performShortcutRequest(entry.id)
        }
    }

    fun requestShortcut(entry: SyncedDisguiseEntry) {
        if (entry.shortcutStatus == ShortcutStatus.TARGET_UNINSTALLED) {
            requestDelete(entry.entry)
            return
        }
        if (entry.shortcutStatus == ShortcutStatus.TARGET_DISABLED) return
        if (entry.shortcutStatus == ShortcutStatus.CREATED) {
            updateExistingShortcut(entry.entry)
            return
        }
        requestShortcut(entry.entry)
    }

    fun confirmDuplicateRequest() {
        val entry = _uiState.value.pendingDuplicateRequest ?: return
        _uiState.update { it.copy(pendingDuplicateRequest = null) }
        performShortcutRequest(entry.id)
    }

    fun cancelDuplicateRequest() {
        _uiState.update { it.copy(pendingDuplicateRequest = null) }
    }

    fun dismissUnsupportedDialog() {
        _uiState.update { it.copy(showUnsupportedDialog = false) }
    }

    fun dismissRequestSubmittedDialog() {
        _uiState.update { it.copy(showRequestSubmittedDialog = false) }
    }

    fun dismissManualDeleteDialog() {
        _uiState.update { it.copy(showManualDeleteDialog = false) }
    }

    fun finishSaved() = resetToList()

    fun back(): Boolean {
        val state = _uiState.value
        when (state.page) {
            AppDisguisePage.List -> return false
            AppDisguisePage.SelectApp -> resetToList()
            AppDisguisePage.SetName -> {
                if (state.editingId == null) {
                    _uiState.update { it.copy(page = AppDisguisePage.SelectApp, error = null) }
                } else {
                    resetToList()
                }
            }
            AppDisguisePage.SelectIcon -> _uiState.update {
                it.copy(page = AppDisguisePage.SetName, error = null)
            }
            AppDisguisePage.Preview -> _uiState.update {
                it.copy(page = AppDisguisePage.SelectIcon, error = null)
            }
            AppDisguisePage.Saved -> resetToList()
            AppDisguisePage.Details -> resetToList()
        }
        return true
    }

    private fun scanApps() {
        if (_uiState.value.isScanningApps) return
        _uiState.update { it.copy(isScanningApps = true, error = null) }
        viewModelScope.launch {
            try {
                val apps = repository.scanInstalledApps()
                _uiState.update {
                    it.copy(
                        installedApps = apps,
                        visibleInstalledApps = filterApps(apps, it.appQuery),
                        isScanningApps = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isScanningApps = false, error = AppDisguiseError.ScanFailed)
                }
            }
        }
    }

    private fun resetToList() {
        _uiState.update {
            it.copy(
                page = AppDisguisePage.List,
                editingId = null,
                selectedApp = null,
                customName = "",
                selectedIcon = DisguiseIconId.Files,
                isSaving = false,
                selectedDetails = null,
                savedEntry = null,
                pendingDelete = null,
                error = null,
            )
        }
    }

    private fun performShortcutRequest(entryId: Long) {
        val useCase = requestPinShortcutUseCase ?: return
        if (_uiState.value.requestingShortcutEntryId != null) return
        _uiState.update { it.copy(requestingShortcutEntryId = entryId) }
        viewModelScope.launch {
            when (useCase(entryId)) {
                PinShortcutRequestResult.RequestSubmitted -> _uiState.update {
                    it.copy(
                        requestingShortcutEntryId = null,
                        showRequestSubmittedDialog = true,
                    )
                }
                PinShortcutRequestResult.RequestSubmittedStateSaveFailed -> {
                    _uiState.update { it.copy(requestingShortcutEntryId = null) }
                    _effects.send(AppDisguiseEffect.ShortcutRequestStateSaveFailed)
                }
                PinShortcutRequestResult.Unsupported -> _uiState.update {
                    it.copy(
                        requestingShortcutEntryId = null,
                        showUnsupportedDialog = true,
                    )
                }
                PinShortcutRequestResult.AlreadyRequesting -> {
                    _uiState.update { it.copy(requestingShortcutEntryId = null) }
                }
                else -> {
                    _uiState.update { it.copy(requestingShortcutEntryId = null) }
                    _effects.send(AppDisguiseEffect.ShortcutRequestFailed)
                }
            }
            refreshShortcutStatus()
        }
    }

    private fun updateExistingShortcut(entry: DisguiseEntry) {
        val shortcutId = entry.shortcutId ?: return
        val shortcuts = shortcutRepository ?: return
        viewModelScope.launch {
            when (
                shortcuts.update(
                    ShortcutUpdateRequest(shortcutId, entry.customName, entry.iconId),
                )
            ) {
                ShortcutOperationResult.Success -> _effects.send(AppDisguiseEffect.ShortcutUpdated)
                ShortcutOperationResult.NotFound -> refreshShortcutStatus()
                else -> _effects.send(AppDisguiseEffect.ShortcutRequestFailed)
            }
        }
    }

    private fun filterApps(
        apps: List<com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp>,
        query: String,
    ) = query.trim().takeIf(String::isNotEmpty)?.let { normalized ->
        apps.filter { it.appName.contains(normalized, ignoreCase = true) }
    } ?: apps

    private fun filterAndSort(
        entries: List<SyncedDisguiseEntry>,
        query: String,
        mode: DisguiseSortMode,
    ): List<SyncedDisguiseEntry> {
        val normalized = query.trim()
        val filtered = entries.filter {
            normalized.isEmpty() ||
                it.entry.customName.contains(normalized, ignoreCase = true) ||
                it.entry.targetAppName.contains(normalized, ignoreCase = true)
        }
        return when (mode) {
            DisguiseSortMode.CreatedNewest -> filtered.sortedByDescending { it.entry.createdAt }
            DisguiseSortMode.UpdatedNewest -> filtered.sortedByDescending { it.entry.updatedAt }
            DisguiseSortMode.Name -> filtered.sortedWith { left, right ->
                collator.compare(left.entry.customName, right.entry.customName)
            }
        }
    }

    private suspend fun syncEntries(entries: List<DisguiseEntry>): List<SyncedDisguiseEntry> =
        shortcutSyncManager?.sync(entries)
            ?: entries.map { SyncedDisguiseEntry(it, fallbackShortcutStatus(it)) }

    private fun fallbackShortcutStatus(entry: DisguiseEntry): ShortcutStatus = when {
        entry.shortcutRequestState == ShortcutRequestState.LauncherAccepted ->
            ShortcutStatus.CREATED
        entry.shortcutRequestState == ShortcutRequestState.RequestSubmitted ->
            ShortcutStatus.NEED_RECREATE
        else -> ShortcutStatus.NOT_CREATED
    }

    class Factory(
        private val repository: DisguiseEntryRepositoryContract,
        private val requestPinShortcutUseCase: RequestPinShortcutUseCase? = null,
        private val shortcutSyncManager: ShortcutSyncManager? = null,
        private val shortcutRepository: ShortcutRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppDisguiseViewModel::class.java))
            return AppDisguiseViewModel(
                repository,
                requestPinShortcutUseCase,
                shortcutSyncManager,
                shortcutRepository,
            ) as T
        }
    }
}
