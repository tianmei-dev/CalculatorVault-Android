package com.aurora.calculatorvault.core.security.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

sealed interface VaultSessionState {
    data object Locked : VaultSessionState
    data object Unlocked : VaultSessionState
}

/**
 * 私密会话仅保存在当前进程内存中。新进程始终从 Locked 开始。
 */
class VaultSessionManager(
    private val externalResultForegroundTimeoutMs: Long = DEFAULT_EXTERNAL_RESULT_FOREGROUND_TIMEOUT_MS,
    private val scheduleExternalResultTimeout: (Long, () -> Unit) -> Unit = { delayMillis, action ->
        externalResultTimeoutExecutor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
    },
) {
    private val _state = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    val state: StateFlow<VaultSessionState> = _state.asStateFlow()

    private val _lockGeneration = MutableStateFlow(0L)
    val lockGeneration: StateFlow<Long> = _lockGeneration.asStateFlow()

    @Volatile
    private var appInForeground = false

    @Volatile
    private var externalResultInProgress = false

    private var externalResultToken = 0L

    @Synchronized
    fun onAppForegrounded() {
        appInForeground = true
        scheduleExternalResultRelockIfNeeded()
    }

    @Synchronized
    fun onAppBackgrounded() {
        appInForeground = false
        if (externalResultInProgress) return
        lock()
    }

    /**
     * 用于系统 Activity Result 场景（如 Photo Picker）。
     *
     * 这类系统选择器会短暂让本 App 进入后台；如果立刻锁定，会导致选择完成后
     * 被导航守卫踢回计算器，丢失导入回调。该豁免只保持到 App 回到前台或显式结束。
     */
    @Synchronized
    fun beginExternalResultFlow() {
        externalResultInProgress = true
        externalResultToken += 1L
    }

    @Synchronized
    fun endExternalResultFlow() {
        externalResultInProgress = false
        externalResultToken += 1L
        appInForeground = true
    }

    @Synchronized
    fun cancelExternalResultFlowAndLock() {
        externalResultInProgress = false
        externalResultToken += 1L
        appInForeground = true
        lock()
    }

    @Synchronized
    fun onHostActivityResumed() {
        appInForeground = true
        scheduleExternalResultRelockIfNeeded()
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

    private fun scheduleExternalResultRelockIfNeeded() {
        if (externalResultInProgress) {
            val token = ++externalResultToken
            scheduleExternalResultTimeout(externalResultForegroundTimeoutMs) {
                lockIfExternalResultStillPending(token)
            }
        }
    }

    @Synchronized
    private fun lockIfExternalResultStillPending(token: Long) {
        if (externalResultInProgress && appInForeground && token == externalResultToken) {
            externalResultInProgress = false
            lock()
        }
    }

    private companion object {
        const val DEFAULT_EXTERNAL_RESULT_FOREGROUND_TIMEOUT_MS = 500L
        val externalResultTimeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "VaultExternalResultTimeout").apply {
                isDaemon = true
            }
        }
    }
}
