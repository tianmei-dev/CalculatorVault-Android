package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId

data class PinShortcutRequest(
    val shortcutId: String,
    val displayName: String,
    val iconId: DisguiseIconId,
)

sealed interface PinShortcutRequestResult {
    data object RequestSubmitted : PinShortcutRequestResult
    data object RequestSubmittedStateSaveFailed : PinShortcutRequestResult
    data object Unsupported : PinShortcutRequestResult
    data object EntryNotFound : PinShortcutRequestResult
    data object InvalidConfiguration : PinShortcutRequestResult
    data object MissingShortcutId : PinShortcutRequestResult
    data object IconGenerationFailed : PinShortcutRequestResult
    data object RequestRejectedImmediately : PinShortcutRequestResult
    data object AlreadyRequesting : PinShortcutRequestResult
    data object SecurityBlocked : PinShortcutRequestResult
    data object Failed : PinShortcutRequestResult
}

interface PinnedShortcutCreator {
    fun isSupported(): Boolean
    suspend fun requestPinShortcut(request: PinShortcutRequest): PinShortcutRequestResult
}

sealed interface ShortcutOperationResult {
    data object Success : ShortcutOperationResult
    data object Unsupported : ShortcutOperationResult
    data object NotFound : ShortcutOperationResult
    data object IconGenerationFailed : ShortcutOperationResult
    data object SecurityBlocked : ShortcutOperationResult
    data object ManualRemovalRequired : ShortcutOperationResult
    data object Failed : ShortcutOperationResult
}
