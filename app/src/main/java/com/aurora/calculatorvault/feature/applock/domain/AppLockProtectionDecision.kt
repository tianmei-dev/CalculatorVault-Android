package com.aurora.calculatorvault.feature.applock.domain

/** Pure inputs for whether app-lock monitoring should run. */
data class AppLockProtectionInputs(
    val entitled: Boolean,
    val lockedPackageCount: Int,
    val privacyOk: Boolean,
    val usageAccess: Boolean,
    val overlayPermission: Boolean,
    val processForeground: Boolean,
    val monitorRunning: Boolean,
)

sealed interface AppLockProtectionCommand {
    /** Hide overlay, cancel verify, clear temporary unlock, bump generation. */
    data object RevokeRuntime : AppLockProtectionCommand
    data object StopMonitor : AppLockProtectionCommand
    data object StartMonitor : AppLockProtectionCommand
    /** Keep Room config; retry start when the process is foreground again. */
    data object DeferUntilForeground : AppLockProtectionCommand
    data object None : AppLockProtectionCommand
}

/**
 * Decides start/stop without mutating Room configuration.
 * Callers apply [RevokeRuntime] before [StopMonitor] when both are needed.
 */
object AppLockProtectionDecision {
    fun decide(input: AppLockProtectionInputs): List<AppLockProtectionCommand> {
        val shouldRun = input.entitled &&
            input.lockedPackageCount > 0 &&
            input.privacyOk &&
            input.usageAccess &&
            input.overlayPermission

        if (!shouldRun) {
            return buildList {
                add(AppLockProtectionCommand.RevokeRuntime)
                if (input.monitorRunning) add(AppLockProtectionCommand.StopMonitor)
            }
        }

        if (input.monitorRunning) return listOf(AppLockProtectionCommand.None)

        return if (input.processForeground) {
            listOf(AppLockProtectionCommand.StartMonitor)
        } else {
            listOf(AppLockProtectionCommand.DeferUntilForeground)
        }
    }
}
