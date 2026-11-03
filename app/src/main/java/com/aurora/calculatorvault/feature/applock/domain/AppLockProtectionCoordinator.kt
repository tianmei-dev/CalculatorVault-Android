package com.aurora.calculatorvault.feature.applock.domain

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.ActionPolicy
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped owner of app-lock monitor lifecycle.
 * ViewModels must call [reconcile] instead of starting/stopping the service directly.
 */
class AppLockProtectionCoordinator(
    private val entitlementState: StateFlow<EntitlementState>,
    private val actionPolicy: ActionPolicy,
    private val observeLockedPackages: () -> Flow<Set<String>>,
    /** Suspend so callers always read a fresh Room-backed count (avoids AtomicInteger lag). */
    private val lockedPackageCount: suspend () -> Int,
    private val privacyOk: suspend () -> Boolean,
    private val privacyWithdrawn: () -> Boolean,
    private val usageAccessGranted: () -> Boolean,
    private val overlayPermissionGranted: () -> Boolean,
    private val processForeground: () -> Boolean,
    private val isMonitorRunning: () -> Boolean,
    private val startMonitor: () -> Boolean,
    private val stopMonitor: () -> Unit,
    private val revokeRuntimeProtection: () -> Unit,
    private val scope: CoroutineScope,
) {
    private val generation = AtomicLong(0L)
    private val reconcileGate = Any()
    private val deferredStart = MutableStateFlow(false)
    private var collectJob: Job? = null

    private val _pendingForegroundStart = MutableStateFlow(false)
    val pendingForegroundStart: StateFlow<Boolean> = _pendingForegroundStart.asStateFlow()

    fun protectionGeneration(): Long = generation.get()

    fun bumpGeneration(): Long = generation.incrementAndGet()

    /** Live entitlement check for Service / Overlay commit boundaries. */
    fun allowsLiveProtection(): Boolean {
        if (privacyWithdrawn()) return false
        return actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION) is ActionDecision.Allowed
    }

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            combine(
                entitlementState,
                observeLockedPackages(),
                deferredStart,
            ) { entitlement, packages, deferred ->
                Triple(entitlement.source, packages.size, deferred)
            }.distinctUntilChanged().collect {
                reconcile()
            }
        }
    }

    fun onProcessForeground() {
        deferredStart.value = true
        scope.launch { reconcile() }
    }

    fun onPermissionsOrPrivacyChanged() {
        scope.launch { reconcile() }
    }

    /**
     * Re-evaluate whether monitoring should run.
     * Never mutates Room lock configuration.
     */
    suspend fun reconcile() {
        val privacy = !privacyWithdrawn() && runCatching { privacyOk() }.getOrDefault(false)
        val entitled = allowsLiveProtection()
        // Read Room count before taking the gate so we never start/stop on a stale AtomicInteger.
        val lockedCount = runCatching { lockedPackageCount() }.getOrDefault(0)
        synchronized(reconcileGate) {
            val inputs = AppLockProtectionInputs(
                entitled = entitled,
                lockedPackageCount = lockedCount,
                privacyOk = privacy,
                usageAccess = usageAccessGranted(),
                overlayPermission = overlayPermissionGranted(),
                processForeground = processForeground(),
                monitorRunning = isMonitorRunning(),
            )
            val commands = AppLockProtectionDecision.decide(inputs)
            for (command in commands) {
                when (command) {
                    AppLockProtectionCommand.RevokeRuntime -> {
                        bumpGeneration()
                        revokeRuntimeProtection()
                    }
                    AppLockProtectionCommand.StopMonitor -> {
                        stopMonitor()
                        _pendingForegroundStart.value = false
                    }
                    AppLockProtectionCommand.StartMonitor -> {
                        val started = runCatching { startMonitor() }.getOrDefault(false)
                        _pendingForegroundStart.value = !started
                    }
                    AppLockProtectionCommand.DeferUntilForeground -> {
                        _pendingForegroundStart.value = true
                    }
                    AppLockProtectionCommand.None -> Unit
                }
            }
            deferredStart.value = false
        }
    }
}
