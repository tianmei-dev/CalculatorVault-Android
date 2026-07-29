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
    val availability: InstalledAppAvailability,
    val lastOpenedAt: Long? = null,
    val openCount: Int = 0,
)

enum class HiddenAppLayoutMode {
    Grid,
    List,
}

enum class HiddenAppSortMode {
    Manual,
    AddedNewest,
    AddedOldest,
    NameAscending,
    NameDescending,
}

enum class InstalledAppAvailability {
    Available,
    NotInstalled,
    Disabled,
    NoLauncher,
    Unknown,
}

data class InstalledAppRuntimeInfo(
    val packageName: String,
    val appName: String?,
    val availability: InstalledAppAvailability,
)

sealed interface AppLaunchResult {
    data object Success : AppLaunchResult
    data object InvalidPackage : AppLaunchResult
    data object NotInstalled : AppLaunchResult
    data object Disabled : AppLaunchResult
    data object NoLaunchIntent : AppLaunchResult
    data object ActivityNotFound : AppLaunchResult
    data object SecurityBlocked : AppLaunchResult
    data object Failed : AppLaunchResult
}

sealed interface HiddenAppError {
    data object ScanFailed : HiddenAppError
    data object LoadFailed : HiddenAppError
    data object SaveFailed : HiddenAppError
    data object RemoveFailed : HiddenAppError
    data object RefreshFailed : HiddenAppError
    data object NotInstalled : HiddenAppError
    data object Disabled : HiddenAppError
    data object NoLaunchIntent : HiddenAppError
    data object LaunchBlocked : HiddenAppError
    data object LaunchFailed : HiddenAppError
    data object ClearRecentFailed : HiddenAppError
    data object PreferenceSaveFailed : HiddenAppError
    data object ManualSortFailed : HiddenAppError
    data object BatchRemoveFailed : HiddenAppError
}
