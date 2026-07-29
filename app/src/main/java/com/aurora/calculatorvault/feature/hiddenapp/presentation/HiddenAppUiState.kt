package com.aurora.calculatorvault.feature.hiddenapp.presentation

import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppLayoutMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppSortMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import java.text.Collator

data class HiddenAppPickerUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val addedPackages: Set<String> = emptySet(),
    val query: String = "",
    val selectedPackages: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val error: HiddenAppError? = null,
) {
    val visibleApps: List<InstalledApp>
        get() {
            val normalized = query.trim()
            return if (normalized.isEmpty()) apps else apps.filter {
                it.appName.contains(normalized, ignoreCase = true)
            }
        }

    val canSave: Boolean
        get() = selectedPackages.isNotEmpty() && !isSaving
}

sealed interface HiddenAppPickerEffect {
    data class Completed(val addedCount: Int) : HiddenAppPickerEffect
}

data class HiddenAppUiState(
    val isLoading: Boolean = true,
    val apps: List<HiddenApp> = emptyList(),
    val recentApps: List<HiddenApp> = emptyList(),
    val query: String = "",
    val launchingPackageName: String? = null,
    val pendingRemoval: HiddenApp? = null,
    val launchErrorApp: HiddenApp? = null,
    val pendingClearRecent: Boolean = false,
    val isRemoving: Boolean = false,
    val isClearingRecent: Boolean = false,
    val layoutMode: HiddenAppLayoutMode = HiddenAppLayoutMode.Grid,
    val sortMode: HiddenAppSortMode = HiddenAppSortMode.Manual,
    val showSortDialog: Boolean = false,
    val showUsageNotice: Boolean = true,
    val isBatchMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val isBatchRemoving: Boolean = false,
    val pendingBatchRemoval: Boolean = false,
    val isManualSortMode: Boolean = false,
    val pendingManualOrder: List<String> = emptyList(),
    val isSavingManualOrder: Boolean = false,
    val showDiscardManualOrder: Boolean = false,
    val selectedDetailApp: HiddenApp? = null,
    val error: HiddenAppError? = null,
) {
    val orderedApps: List<HiddenApp>
        get() = when (sortMode) {
            HiddenAppSortMode.Manual -> {
                val order = pendingManualOrder.withIndex().associate { it.value to it.index }
                apps.sortedWith(
                    compareBy<HiddenApp> { order[it.packageName] ?: it.sortOrder }
                        .thenBy(HiddenApp::addedAt),
                )
            }
            HiddenAppSortMode.AddedNewest -> apps.sortedByDescending(HiddenApp::addedAt)
            HiddenAppSortMode.AddedOldest -> apps.sortedBy(HiddenApp::addedAt)
            HiddenAppSortMode.NameAscending -> apps.sortedWith(nameComparator())
            HiddenAppSortMode.NameDescending -> apps.sortedWith(nameComparator().reversed())
        }

    val visibleApps: List<HiddenApp>
        get() {
            val normalized = query.trim()
            return if (normalized.isEmpty()) orderedApps else orderedApps.filter {
                it.appName.contains(normalized, ignoreCase = true) ||
                    it.packageName.contains(normalized, ignoreCase = true)
            }
        }

    val areAllVisibleSelected: Boolean
        get() = visibleApps.isNotEmpty() &&
            visibleApps.all { it.packageName in selectedPackages }

    val invalidVisiblePackages: Set<String>
        get() = visibleApps
            .filter {
                it.availability == InstalledAppAvailability.NotInstalled ||
                    it.availability == InstalledAppAvailability.NoLauncher
            }
            .mapTo(mutableSetOf(), HiddenApp::packageName)

    private fun nameComparator(): Comparator<HiddenApp> {
        val collator = Collator.getInstance()
        return Comparator { first, second ->
            val nameResult = collator.compare(first.appName, second.appName)
            if (nameResult != 0) nameResult else first.packageName.compareTo(second.packageName)
        }
    }
}

sealed interface HiddenAppEffect {
    data object Removed : HiddenAppEffect
    data object RecentCleared : HiddenAppEffect
    data class BatchRemoved(val count: Int) : HiddenAppEffect
    data object NoInvalidApps : HiddenAppEffect
    data object PreferenceSaveFailed : HiddenAppEffect
    data object ManualOrderSaveFailed : HiddenAppEffect
    data object BatchRemoveFailed : HiddenAppEffect
    data object RemoveFailed : HiddenAppEffect
    data object ClearRecentFailed : HiddenAppEffect
}
