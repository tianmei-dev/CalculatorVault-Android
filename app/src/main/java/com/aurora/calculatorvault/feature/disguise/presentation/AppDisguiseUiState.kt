package com.aurora.calculatorvault.feature.disguise.presentation

import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseSortMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp

enum class AppDisguisePage {
    List,
    SelectApp,
    SetName,
    SelectIcon,
    Preview,
    Saved,
    Details,
}

data class AppDisguiseUiState(
    val page: AppDisguisePage = AppDisguisePage.List,
    val entries: List<DisguiseEntry> = emptyList(),
    val visibleEntries: List<DisguiseEntry> = emptyList(),
    val query: String = "",
    val sortMode: DisguiseSortMode = DisguiseSortMode.UpdatedNewest,
    val isLoading: Boolean = true,
    val isScanningApps: Boolean = false,
    val installedApps: List<InstalledApp> = emptyList(),
    val visibleInstalledApps: List<InstalledApp> = emptyList(),
    val appQuery: String = "",
    val editingId: Long? = null,
    val selectedApp: InstalledApp? = null,
    val customName: String = "",
    val selectedIcon: DisguiseIconId = DisguiseIconId.Files,
    val isSaving: Boolean = false,
    val pendingDelete: DisguiseEntry? = null,
    val selectedDetails: DisguiseEntry? = null,
    val savedEntry: DisguiseEntry? = null,
    val requestingShortcutEntryId: Long? = null,
    val pendingDuplicateRequest: DisguiseEntry? = null,
    val showUnsupportedDialog: Boolean = false,
    val showRequestSubmittedDialog: Boolean = false,
    val error: AppDisguiseError? = null,
) {
    val canContinueName: Boolean
        get() = customName.isNotBlank()
}

enum class AppDisguiseError {
    LoadFailed,
    ScanFailed,
    SaveFailed,
    DeleteFailed,
}

sealed interface AppDisguiseEffect {
    data object Saved : AppDisguiseEffect
    data object Updated : AppDisguiseEffect
    data object Deleted : AppDisguiseEffect
    data object ShortcutRequestFailed : AppDisguiseEffect
    data object ShortcutRequestStateSaveFailed : AppDisguiseEffect
}
