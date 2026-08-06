package com.aurora.calculatorvault.feature.disguise.shortcut

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DisguiseShortcutEntryViewModel(
    private val resolveShortcut: DisguiseShortcutResolver,
    private val verifyPassword: VaultPasswordVerification,
    private val launchTarget: DisguisedTargetLauncher,
    private val session: DisguiseShortcutSession = DisguiseShortcutSession(),
) : ViewModel() {
    private val password = CharArray(MAX_PASSWORD_LENGTH)
    private var passwordLength = 0
    private var operationGeneration = 0L
    private var operationJob: Job? = null
    private var expiryJob: Job? = null

    private val _state = MutableStateFlow<DisguiseShortcutEntryState>(
        DisguiseShortcutEntryState.Resolving,
    )
    val state: StateFlow<DisguiseShortcutEntryState> = _state.asStateFlow()

    private val _effects = Channel<DisguiseShortcutEntryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun acceptIntent(result: DisguiseShortcutIntentResult) {
        resetTransientState()
        when (result) {
            DisguiseShortcutIntentResult.Invalid -> {
                _state.value = DisguiseShortcutEntryState.InvalidRequest
            }
            is DisguiseShortcutIntentResult.Valid -> resolve(result.shortcutId)
        }
    }

    fun inputDigit(digit: Int) {
        val current = _state.value as? DisguiseShortcutEntryState.AwaitingPassword ?: return
        if (digit !in 0..9 || passwordLength >= MAX_PASSWORD_LENGTH) return
        password[passwordLength++] = ('0'.code + digit).toChar()
        _state.value = current.copy(enteredLength = passwordLength, passwordIncorrect = false)
    }

    fun deleteDigit() {
        val current = _state.value as? DisguiseShortcutEntryState.AwaitingPassword ?: return
        if (passwordLength > 0) password[--passwordLength] = NULL_CHAR
        _state.value = current.copy(enteredLength = passwordLength, passwordIncorrect = false)
    }

    fun clearInput() {
        val current = _state.value as? DisguiseShortcutEntryState.AwaitingPassword ?: return
        wipePassword()
        _state.value = current.copy(enteredLength = 0, passwordIncorrect = false)
    }

    fun confirmPassword() {
        if (_state.value !is DisguiseShortcutEntryState.AwaitingPassword) return
        if (passwordLength !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) return
        if (!session.tryStartVerification()) return
        val generation = operationGeneration
        val candidate = password.copyOf(passwordLength)
        wipePassword()
        _state.value = DisguiseShortcutEntryState.VerifyingPassword
        operationJob = viewModelScope.launch {
            val verified = try {
                verifyPassword(candidate)
            } catch (_: Exception) {
                false
            } finally {
                candidate.fill(NULL_CHAR)
            }
            if (generation != operationGeneration) return@launch
            session.finishVerification()
            if (!verified) {
                _state.value = DisguiseShortcutEntryState.AwaitingPassword(
                    enteredLength = 0,
                    passwordIncorrect = true,
                )
                return@launch
            }
            val shortcutId = session.currentShortcutId()
            if (shortcutId == null || !session.tryStartLaunch()) {
                expire()
                return@launch
            }
            _state.value = DisguiseShortcutEntryState.LaunchingTarget
            val launchResult = try {
                launchTarget(shortcutId)
            } catch (_: Exception) {
                LaunchDisguisedTargetResult.Failed
            }
            if (generation != operationGeneration) return@launch
            when (launchResult) {
                LaunchDisguisedTargetResult.Success -> {
                    clearSessionOnly()
                    _effects.send(DisguiseShortcutEntryEffect.Finish)
                }
                LaunchDisguisedTargetResult.EntryMissing -> showTerminalState(
                    DisguiseShortcutEntryState.ConfigurationMissing,
                )
                LaunchDisguisedTargetResult.TargetNotInstalled -> showTerminalState(
                    DisguiseShortcutEntryState.TargetNotInstalled,
                )
                LaunchDisguisedTargetResult.TargetDisabled -> showTerminalState(
                    DisguiseShortcutEntryState.TargetDisabled,
                )
                LaunchDisguisedTargetResult.NoLaunchIntent -> showTerminalState(
                    DisguiseShortcutEntryState.NoLaunchIntent,
                )
                LaunchDisguisedTargetResult.ActivityNotFound -> showTerminalState(
                    DisguiseShortcutEntryState.LaunchFailed(LaunchFailureReason.ActivityNotFound),
                )
                LaunchDisguisedTargetResult.SecurityBlocked -> showTerminalState(
                    DisguiseShortcutEntryState.LaunchFailed(LaunchFailureReason.SecurityBlocked),
                )
                LaunchDisguisedTargetResult.Failed -> showTerminalState(
                    DisguiseShortcutEntryState.LaunchFailed(LaunchFailureReason.Unknown),
                )
            }
        }
    }

    fun cancel() {
        resetTransientState()
        _effects.trySend(DisguiseShortcutEntryEffect.Finish)
    }

    fun openCalculator() {
        resetTransientState()
        _effects.trySend(DisguiseShortcutEntryEffect.OpenCalculator)
    }

    fun expire() {
        resetTransientState()
        _state.value = DisguiseShortcutEntryState.SessionExpired
    }

    private fun resolve(shortcutId: String) {
        session.begin(shortcutId)
        val generation = operationGeneration
        _state.value = DisguiseShortcutEntryState.Resolving
        operationJob = viewModelScope.launch {
            val result = resolveShortcut(shortcutId)
            if (generation != operationGeneration) return@launch
            _state.value = when (result) {
                ResolveDisguiseShortcutResult.Ready -> DisguiseShortcutEntryState.AwaitingPassword()
                ResolveDisguiseShortcutResult.InvalidShortcutId -> DisguiseShortcutEntryState.InvalidRequest
                ResolveDisguiseShortcutResult.EntryNotFound -> DisguiseShortcutEntryState.ConfigurationMissing
                ResolveDisguiseShortcutResult.TargetNotInstalled -> DisguiseShortcutEntryState.TargetNotInstalled
                ResolveDisguiseShortcutResult.TargetDisabled -> DisguiseShortcutEntryState.TargetDisabled
                ResolveDisguiseShortcutResult.NoLaunchIntent -> DisguiseShortcutEntryState.NoLaunchIntent
                ResolveDisguiseShortcutResult.Failed -> DisguiseShortcutEntryState.LaunchFailed(
                    LaunchFailureReason.Unknown,
                )
            }
            if (result is ResolveDisguiseShortcutResult.Ready) {
                scheduleExpiry(generation)
            } else {
                session.clear()
            }
        }
    }

    private fun scheduleExpiry(generation: Long) {
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            delay(ENTRY_SESSION_TIMEOUT_MILLIS)
            if (generation == operationGeneration) expire()
        }
    }

    private fun showTerminalState(value: DisguiseShortcutEntryState) {
        clearSessionOnly()
        _state.value = value
    }

    private fun resetTransientState() {
        operationGeneration += 1
        operationJob?.cancel()
        operationJob = null
        clearSessionOnly()
    }

    private fun clearSessionOnly() {
        expiryJob?.cancel()
        expiryJob = null
        wipePassword()
        session.clear()
    }

    private fun wipePassword() {
        password.fill(NULL_CHAR)
        passwordLength = 0
    }

    override fun onCleared() {
        resetTransientState()
        _effects.close()
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 4
        const val MAX_PASSWORD_LENGTH = 8
        const val ENTRY_SESSION_TIMEOUT_MILLIS = 2 * 60 * 1_000L
        const val NULL_CHAR = '\u0000'
    }
}
