package com.aurora.calculatorvault.feature.applock.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NotificationPromptStore {
    fun wasOffered(): Boolean
    fun markOffered(): Boolean
}

data class NotificationPermissionSnapshot(
    val apiLevel: Int,
    val runtimeGranted: Boolean,
    val notificationsEnabled: Boolean,
) {
    val needsRuntimePermission: Boolean get() = apiLevel >= 33 && !runtimeGranted
}

data class AppLockNotificationState(
    val snapshot: NotificationPermissionSnapshot,
    val offered: Boolean,
    val showExplanation: Boolean = false,
    val requestInFlight: Boolean = false,
) {
    val showSettingsHint: Boolean
        get() = !snapshot.notificationsEnabled || (offered && snapshot.needsRuntimePermission)
}

/** Notification consent is optional; it never determines whether the app lock can operate. */
class AppLockNotificationCoordinator(private val store: NotificationPromptStore) {
    private val mutableState = MutableStateFlow(AppLockNotificationState(
        NotificationPermissionSnapshot(0, true, true), try { store.wasOffered() } catch (_: Exception) { true }))
    val state = mutableState.asStateFlow()

    /** Page entry/resume only observes system state, never launches a request. */
    fun refresh(snapshot: NotificationPermissionSnapshot) {
        val old = mutableState.value
        mutableState.value = old.copy(snapshot = snapshot,
            showExplanation = old.showExplanation && snapshot.needsRuntimePermission,
            requestInFlight = old.requestInFlight && snapshot.needsRuntimePermission)
    }

    /** Called only after the user's explicit enable operation actually succeeds. */
    fun onLockEnabled(snapshot: NotificationPermissionSnapshot) {
        refresh(snapshot)
        val old = mutableState.value
        if (!snapshot.needsRuntimePermission || old.offered || old.requestInFlight) return
        // If persistence is unavailable, use the settings entry rather than repeated automatic prompts.
        val saved = try { store.markOffered() } catch (_: Exception) { false }
        mutableState.value = old.copy(offered = true, showExplanation = saved)
    }

    fun dismissExplanation() {
        mutableState.value = mutableState.value.copy(showExplanation = false)
    }

    fun confirmRequest(snapshot: NotificationPermissionSnapshot): Boolean {
        val old = mutableState.value
        refresh(snapshot)
        if (!old.showExplanation || !snapshot.needsRuntimePermission || old.requestInFlight) return false
        mutableState.value = mutableState.value.copy(showExplanation = false, requestInFlight = true)
        return true
    }

    fun onResult(snapshot: NotificationPermissionSnapshot) {
        mutableState.value = mutableState.value.copy(snapshot = snapshot,
            showExplanation = false, requestInFlight = false)
    }
}
