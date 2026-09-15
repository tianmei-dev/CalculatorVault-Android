package com.aurora.calculatorvault.core.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PrivacyWithdrawalState { Idle, Saving, Failed, RestartRequired }

/** Application-scoped: rotation/navigation cannot cancel a committed withdrawal. */
class PrivacyWithdrawalController(
    private val scope: CoroutineScope,
    private val persistAndStopSdks: suspend () -> Unit,
    private val endSessions: () -> Unit,
) {
    private val mutableState = MutableStateFlow(PrivacyWithdrawalState.Idle)
    val state = mutableState.asStateFlow()

    fun withdraw() {
        val previous = mutableState.value
        if (previous != PrivacyWithdrawalState.Idle && previous != PrivacyWithdrawalState.Failed) return
        if (!mutableState.compareAndSet(previous, PrivacyWithdrawalState.Saving)) return
        scope.launch {
            try {
                persistAndStopSdks()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = PrivacyWithdrawalState.Failed
                return@launch
            }
            try {
                endSessions()
            } catch (_: Exception) {
                // The replacement process also removes all old windows/services.
            } finally {
                // Even a failed service stop must lead to process replacement.
                mutableState.value = PrivacyWithdrawalState.RestartRequired
            }
        }
    }
}
