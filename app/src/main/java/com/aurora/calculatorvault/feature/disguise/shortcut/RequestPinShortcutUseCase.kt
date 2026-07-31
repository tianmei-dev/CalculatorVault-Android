package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseNamePolicy
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestError
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import kotlinx.coroutines.sync.Mutex

class RequestPinShortcutUseCase(
    private val repository: DisguiseEntryRepositoryContract,
    private val creator: PinnedShortcutCreator,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val requestMutex = Mutex()

    suspend operator fun invoke(entryId: Long): PinShortcutRequestResult {
        if (!requestMutex.tryLock()) return PinShortcutRequestResult.AlreadyRequesting
        try {
            val entry = repository.findById(entryId)
                ?: return PinShortcutRequestResult.EntryNotFound
            val displayName = entry.customName.trim()
            if (!DisguiseNamePolicy.isValid(displayName)) {
                repository.updateShortcutRequest(
                    entryId,
                    ShortcutRequestState.Failed,
                    null,
                    ShortcutRequestError.InvalidConfiguration,
                )
                return PinShortcutRequestResult.InvalidConfiguration
            }
            if (!creator.isSupported()) {
                repository.updateShortcutRequest(
                    entryId,
                    ShortcutRequestState.Unsupported,
                    null,
                    null,
                )
                return PinShortcutRequestResult.Unsupported
            }
            val shortcutId = repository.ensureShortcutId(entryId)
                ?: return PinShortcutRequestResult.MissingShortcutId
            val result = creator.requestPinShortcut(
                PinShortcutRequest(
                    shortcutId = shortcutId,
                    displayName = displayName,
                    iconId = entry.iconId,
                ),
            )
            return persistResult(entryId, result)
        } finally {
            requestMutex.unlock()
        }
    }

    private suspend fun persistResult(
        entryId: Long,
        result: PinShortcutRequestResult,
    ): PinShortcutRequestResult {
        val requestedAt = currentTimeMillis()
        return when (result) {
            PinShortcutRequestResult.RequestSubmitted -> {
                if (
                    repository.updateShortcutRequest(
                        entryId,
                        ShortcutRequestState.RequestSubmitted,
                        requestedAt,
                        null,
                    )
                ) result else PinShortcutRequestResult.RequestSubmittedStateSaveFailed
            }
            PinShortcutRequestResult.Unsupported -> result
            PinShortcutRequestResult.IconGenerationFailed -> {
                repository.updateShortcutRequest(
                    entryId,
                    ShortcutRequestState.Failed,
                    null,
                    ShortcutRequestError.IconGenerationFailed,
                )
                result
            }
            PinShortcutRequestResult.RequestRejectedImmediately -> {
                repository.updateShortcutRequest(
                    entryId,
                    ShortcutRequestState.Failed,
                    null,
                    ShortcutRequestError.RequestRejected,
                )
                result
            }
            PinShortcutRequestResult.SecurityBlocked -> {
                repository.updateShortcutRequest(
                    entryId,
                    ShortcutRequestState.Failed,
                    null,
                    ShortcutRequestError.SecurityBlocked,
                )
                result
            }
            PinShortcutRequestResult.Failed -> {
                repository.updateShortcutRequest(
                    entryId,
                    ShortcutRequestState.Failed,
                    null,
                    ShortcutRequestError.Unknown,
                )
                result
            }
            else -> result
        }
    }
}

