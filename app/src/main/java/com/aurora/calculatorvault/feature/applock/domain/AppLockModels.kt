package com.aurora.calculatorvault.feature.applock.domain

import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability

data class AppLockEntry(
    val packageName: String,
    val appNameSnapshot: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class LockableApp(
    val packageName: String,
    val appName: String,
    val locked: Boolean,
    val availability: InstalledAppAvailability = InstalledAppAvailability.Available,
)

enum class AppLockProtectionStatus {
    Protecting,
    NeedsPermission,
    NotRunning,
    NoLockedApps,
}

enum class AppLockPermissionTarget {
    UsageAccess,
    Overlay,
}

sealed interface AppLockSetResult {
    data object Success : AppLockSetResult
    data object Rejected : AppLockSetResult
    data object Failed : AppLockSetResult
}
