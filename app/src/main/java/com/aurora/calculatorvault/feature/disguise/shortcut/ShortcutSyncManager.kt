package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutStatus
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability

data class SyncedDisguiseEntry(
    val entry: DisguiseEntry,
    val shortcutStatus: ShortcutStatus,
)

class ShortcutSyncManager(
    private val shortcutRepository: ShortcutRepository,
    private val runtime: HiddenAppRuntime,
) {
    suspend fun sync(entries: List<DisguiseEntry>): List<SyncedDisguiseEntry> =
        entries.map { entry ->
            SyncedDisguiseEntry(entry, resolveStatus(entry))
        }

    suspend fun resolveStatus(entry: DisguiseEntry): ShortcutStatus {
        val availability = runCatching { runtime.resolve(entry.packageName).availability }
            .getOrDefault(InstalledAppAvailability.Unknown)
        return when (availability) {
            InstalledAppAvailability.NotInstalled -> ShortcutStatus.TARGET_UNINSTALLED
            InstalledAppAvailability.Disabled -> ShortcutStatus.TARGET_DISABLED
            InstalledAppAvailability.NoLauncher,
            InstalledAppAvailability.Unknown,
            -> ShortcutStatus.CONFIG_INVALID
            InstalledAppAvailability.Available -> resolveShortcutPresence(entry)
        }
    }

    private suspend fun resolveShortcutPresence(entry: DisguiseEntry): ShortcutStatus {
        val shortcutId = entry.shortcutId
        if (!DisguiseShortcutIdValidator.isValid(shortcutId)) return ShortcutStatus.NOT_CREATED
        val present = shortcutRepository.isShortcutPresent(requireNotNull(shortcutId))
        if (present) return ShortcutStatus.CREATED
        return when (entry.shortcutRequestState) {
            ShortcutRequestState.RequestSubmitted,
            ShortcutRequestState.LauncherAccepted,
            -> ShortcutStatus.NEED_RECREATE
            ShortcutRequestState.NotRequested,
            ShortcutRequestState.Unsupported,
            ShortcutRequestState.Failed,
            -> ShortcutStatus.NOT_CREATED
        }
    }
}
