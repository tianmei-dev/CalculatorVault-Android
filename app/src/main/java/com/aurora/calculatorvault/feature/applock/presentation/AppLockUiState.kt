package com.aurora.calculatorvault.feature.applock.presentation

import com.aurora.calculatorvault.feature.applock.domain.AppLockPermissionTarget
import com.aurora.calculatorvault.feature.applock.domain.AppLockProtectionStatus
import com.aurora.calculatorvault.feature.applock.domain.LockableApp

data class AppLockUiState(
    val isLoading: Boolean = true,
    val isRefreshingPermissions: Boolean = false,
    val apps: List<LockableApp> = emptyList(),
    val lockedPackages: Set<String> = emptySet(),
    val query: String = "",
    val updatingPackage: String? = null,
    val hasUsageAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isMonitorRunning: Boolean = false,
    val pendingPermissionTarget: AppLockPermissionTarget? = null,
    val error: AppLockError? = null,
) {
    val visibleApps: List<LockableApp>
        get() {
            val normalized = query.trim()
            if (normalized.isEmpty()) return apps
            return apps.filter {
                it.appName.contains(normalized, ignoreCase = true) ||
                    it.packageName.contains(normalized, ignoreCase = true)
            }
        }

    val lockedCount: Int get() = lockedPackages.size

    val protectionStatus: AppLockProtectionStatus
        get() = when {
            lockedPackages.isEmpty() -> AppLockProtectionStatus.NoLockedApps
            !hasUsageAccess || !hasOverlayPermission -> AppLockProtectionStatus.NeedsPermission
            !isMonitorRunning -> AppLockProtectionStatus.NotRunning
            else -> AppLockProtectionStatus.Protecting
        }
}

sealed interface AppLockError {
    data object LoadFailed : AppLockError
    data object SaveFailed : AppLockError
    data object PackageRejected : AppLockError
}

sealed interface AppLockEffect {
    data object SaveFailed : AppLockEffect
    data object PackageRejected : AppLockEffect
}
