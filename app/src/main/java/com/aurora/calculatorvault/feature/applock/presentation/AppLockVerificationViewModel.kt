package com.aurora.calculatorvault.feature.applock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.applock.domain.AppLockSessionManager
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Arrays

class AppLockVerificationViewModel(
    private val targetPackageName: String,
    private val unlockUseCase: VaultUnlockUseCase,
    private val sessionManager: AppLockSessionManager,
) : ViewModel() {
    private val passwordBuffer = CharArray(MAX_PASSWORD_LENGTH)
    private var passwordLength = 0

    private val _state = MutableStateFlow(
        AppLockVerificationState(
            targetPackageName = targetPackageName,
            invalidRequest = targetPackageName.isBlank(),
        ),
    )
    val state: StateFlow<AppLockVerificationState> = _state.asStateFlow()

    private val effectChannel = Channel<AppLockVerificationEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun inputDigit(value: Int) {
        if (value !in 0..9 || passwordLength >= MAX_PASSWORD_LENGTH || _state.value.isVerifying) return
        passwordBuffer[passwordLength] = value.digitToChar()
        passwordLength += 1
        _state.update {
            it.copy(
                enteredLength = passwordLength,
                passwordIncorrect = false,
            )
        }
    }

    fun deleteDigit() {
        if (passwordLength <= 0 || _state.value.isVerifying) return
        passwordLength -= 1
        passwordBuffer[passwordLength] = '\u0000'
        _state.update {
            it.copy(
                enteredLength = passwordLength,
                passwordIncorrect = false,
            )
        }
    }

    fun clearInput() {
        clearPasswordBuffer()
        _state.update {
            it.copy(
                enteredLength = 0,
                passwordIncorrect = false,
            )
        }
    }

    fun confirmPassword() {
        if (_state.value.isVerifying || _state.value.invalidRequest || passwordLength !in 4..8) return
        val candidate = passwordBuffer.copyOf(passwordLength)
        _state.update { it.copy(isVerifying = true, passwordIncorrect = false) }
        viewModelScope.launch {
            val verified = runCatching { unlockUseCase.verify(candidate) }.getOrDefault(false)
            Arrays.fill(candidate, '\u0000')
            if (verified) {
                clearPasswordBuffer()
                sessionManager.markUnlocked(targetPackageName)
                effectChannel.send(AppLockVerificationEffect.Finish)
            } else {
                clearPasswordBuffer()
                _state.update {
                    it.copy(
                        enteredLength = 0,
                        isVerifying = false,
                        passwordIncorrect = true,
                    )
                }
            }
        }
    }

    fun cancel() {
        clearPasswordBuffer()
        sessionManager.finishVerification(targetPackageName)
        viewModelScope.launch {
            effectChannel.send(AppLockVerificationEffect.Finish)
        }
    }

    override fun onCleared() {
        clearPasswordBuffer()
        sessionManager.finishVerification(targetPackageName)
        super.onCleared()
    }

    private fun clearPasswordBuffer() {
        Arrays.fill(passwordBuffer, '\u0000')
        passwordLength = 0
    }

    private companion object {
        const val MAX_PASSWORD_LENGTH = 8
    }
}
