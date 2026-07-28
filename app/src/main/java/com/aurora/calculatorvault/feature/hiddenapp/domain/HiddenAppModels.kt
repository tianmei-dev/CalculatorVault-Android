package com.aurora.calculatorvault.feature.hiddenapp.domain

data class InstalledApp(
    val packageName: String,
    val appName: String,
)

data class HiddenApp(
    val packageName: String,
    val appName: String,
    val appNameSnapshot: String,
    val addedAt: Long,
    val sortOrder: Int,
    val isInstalled: Boolean,
)

sealed interface HiddenAppError {
    data object ScanFailed : HiddenAppError
    data object LoadFailed : HiddenAppError
    data object SaveFailed : HiddenAppError
    data object RemoveFailed : HiddenAppError
}
