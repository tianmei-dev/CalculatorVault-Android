package com.aurora.calculatorvault.feature.hiddenapp.presentation

import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp

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
    val query: String = "",
    val pendingRemoval: HiddenApp? = null,
    val isRemoving: Boolean = false,
    val error: HiddenAppError? = null,
) {
    val visibleApps: List<HiddenApp>
        get() {
            val normalized = query.trim()
            return if (normalized.isEmpty()) apps else apps.filter {
                it.appName.contains(normalized, ignoreCase = true)
            }
        }
}

sealed interface HiddenAppEffect {
    data object Removed : HiddenAppEffect
}
