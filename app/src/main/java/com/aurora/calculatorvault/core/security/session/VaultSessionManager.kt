package com.aurora.calculatorvault.core.security.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface VaultSessionState {
    data object Locked : VaultSessionState
    data object Unlocked : VaultSessionState
}

/**
 * 私密会话仅保存在当前进程内存中。新进程始终从 Locked 开始。
 */
class VaultSessionManager {
    private val _state = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    val state: StateFlow<VaultSessionState> = _state.asStateFlow()

    private val _lockGeneration = MutableStateFlow(0L)
    val lockGeneration: StateFlow<Long> = _lockGeneration.asStateFlow()

    @Volatile
    private var appInForeground = false

    @Synchronized
    fun onAppForegrounded() {
        appInForeground = true
    }

    @Synchronized
    fun onAppBackgrounded() {
        appInForeground = false
        lock()
    }

    /**
     * 只有验证完成时 App 仍在前台，才允许建立会话。
     */
    @Synchronized
    fun tryUnlock(): Boolean {
        if (!appInForeground) return false
        _state.value = VaultSessionState.Unlocked
        return true
    }

    @Synchronized
    fun lock() {
        _state.value = VaultSessionState.Locked
        _lockGeneration.update { it + 1L }
    }

    fun isUnlocked(): Boolean = state.value == VaultSessionState.Unlocked
}
