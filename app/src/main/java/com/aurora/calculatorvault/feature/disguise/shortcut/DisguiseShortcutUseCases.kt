package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import com.aurora.calculatorvault.feature.applock.domain.AppLockRepository
import com.aurora.calculatorvault.feature.applock.domain.AppLockSessionManager
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase

sealed interface ResolveDisguiseShortcutResult {
    data object Ready : ResolveDisguiseShortcutResult
    data object InvalidShortcutId : ResolveDisguiseShortcutResult
    data object EntryNotFound : ResolveDisguiseShortcutResult
    data object TargetNotInstalled : ResolveDisguiseShortcutResult
    data object TargetDisabled : ResolveDisguiseShortcutResult
    data object NoLaunchIntent : ResolveDisguiseShortcutResult
    data object Failed : ResolveDisguiseShortcutResult
}

fun interface DisguiseShortcutResolver {
    suspend operator fun invoke(shortcutId: String): ResolveDisguiseShortcutResult
}

class ResolveDisguiseShortcutUseCase(
    private val repository: DisguiseEntryRepositoryContract,
    private val runtime: HiddenAppRuntime,
) : DisguiseShortcutResolver {
    override suspend fun invoke(shortcutId: String): ResolveDisguiseShortcutResult {
        if (!DisguiseShortcutIdValidator.isValid(shortcutId)) {
            return ResolveDisguiseShortcutResult.InvalidShortcutId
        }
        return try {
            val entry = repository.findByShortcutId(shortcutId)
                ?: return ResolveDisguiseShortcutResult.EntryNotFound
            when (runtime.resolve(entry.packageName).availability) {
                InstalledAppAvailability.Available -> ResolveDisguiseShortcutResult.Ready
                InstalledAppAvailability.NotInstalled -> ResolveDisguiseShortcutResult.TargetNotInstalled
                InstalledAppAvailability.Disabled -> ResolveDisguiseShortcutResult.TargetDisabled
                InstalledAppAvailability.NoLauncher -> ResolveDisguiseShortcutResult.NoLaunchIntent
                InstalledAppAvailability.Unknown -> ResolveDisguiseShortcutResult.Failed
            }
        } catch (_: Exception) {
            ResolveDisguiseShortcutResult.Failed
        }
    }
}

fun interface VaultPasswordVerification {
    suspend operator fun invoke(password: CharArray): Boolean
}

class VerifyVaultPasswordUseCase(
    private val vaultUnlockUseCase: VaultUnlockUseCase,
) : VaultPasswordVerification {
    override suspend fun invoke(password: CharArray): Boolean = vaultUnlockUseCase.verify(password)
}

sealed interface LaunchDisguisedTargetResult {
    data object Success : LaunchDisguisedTargetResult
    data object EntryMissing : LaunchDisguisedTargetResult
    data object TargetNotInstalled : LaunchDisguisedTargetResult
    data object TargetDisabled : LaunchDisguisedTargetResult
    data object NoLaunchIntent : LaunchDisguisedTargetResult
    data object ActivityNotFound : LaunchDisguisedTargetResult
    data object SecurityBlocked : LaunchDisguisedTargetResult
    data object Failed : LaunchDisguisedTargetResult
}

fun interface DisguisedTargetLauncher {
    suspend operator fun invoke(shortcutId: String): LaunchDisguisedTargetResult
}

class LaunchDisguisedTargetUseCase(
    private val repository: DisguiseEntryRepositoryContract,
    private val runtime: HiddenAppRuntime,
    private val launchHiddenAppUseCase: LaunchHiddenAppUseCase,
    private val appLockSessionManager: AppLockSessionManager? = null,
    private val appLockRepository: AppLockRepository? = null,
) : DisguisedTargetLauncher {
    override suspend fun invoke(shortcutId: String): LaunchDisguisedTargetResult {
        if (!DisguiseShortcutIdValidator.isValid(shortcutId)) {
            return LaunchDisguisedTargetResult.EntryMissing
        }
        return try {
            val entry = repository.findByShortcutId(shortcutId)
                ?: return LaunchDisguisedTargetResult.EntryMissing
            when (runtime.resolve(entry.packageName).availability) {
                InstalledAppAvailability.NotInstalled -> return LaunchDisguisedTargetResult.TargetNotInstalled
                InstalledAppAvailability.Disabled -> return LaunchDisguisedTargetResult.TargetDisabled
                InstalledAppAvailability.NoLauncher -> return LaunchDisguisedTargetResult.NoLaunchIntent
                InstalledAppAvailability.Unknown -> return LaunchDisguisedTargetResult.Failed
                InstalledAppAvailability.Available -> Unit
            }
            val shouldTemporarilyUnlock = appLockRepository?.isLocked(entry.packageName) == true
            if (shouldTemporarilyUnlock) {
                appLockSessionManager?.markUnlocked(entry.packageName)
            }
            val launchResult = launchHiddenAppUseCase(entry.packageName).also { result ->
                if (shouldTemporarilyUnlock && result != AppLaunchResult.Success) {
                    appLockSessionManager?.clearUnlocked(entry.packageName)
                }
            }
            when (launchResult) {
                AppLaunchResult.Success -> LaunchDisguisedTargetResult.Success
                AppLaunchResult.NotInstalled -> LaunchDisguisedTargetResult.TargetNotInstalled
                AppLaunchResult.Disabled -> LaunchDisguisedTargetResult.TargetDisabled
                AppLaunchResult.NoLaunchIntent -> LaunchDisguisedTargetResult.NoLaunchIntent
                AppLaunchResult.ActivityNotFound -> LaunchDisguisedTargetResult.ActivityNotFound
                AppLaunchResult.SecurityBlocked -> LaunchDisguisedTargetResult.SecurityBlocked
                AppLaunchResult.InvalidPackage,
                AppLaunchResult.Failed,
                -> LaunchDisguisedTargetResult.Failed
            }
        } catch (_: Exception) {
            LaunchDisguisedTargetResult.Failed
        }
    }
}
