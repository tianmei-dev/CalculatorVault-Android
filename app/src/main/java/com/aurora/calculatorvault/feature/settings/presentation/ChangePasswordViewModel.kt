package com.aurora.calculatorvault.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.onboarding.domain.PasswordPolicy
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordFailure
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordRepositoryContract
import com.aurora.calculatorvault.feature.settings.data.ChangePasswordResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChangePasswordViewModel(
    private val repository: ChangePasswordRepositoryContract,
) : ViewModel() {
    private val currentPassword = CharArray(PasswordPolicy.MAX_LENGTH)
    private val newPassword = CharArray(PasswordPolicy.MAX_LENGTH)
    private val confirmation = CharArray(PasswordPolicy.MAX_LENGTH)
    private var currentLength = 0
    private var newLength = 0
    private var confirmationLength = 0
    private var currentPasswordVerified = false

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun addDigit(digit: Int) {
        if (digit !in 0..9 || _uiState.value.isProcessing || _uiState.value.showSamePasswordPrompt) {
            return
        }
        when (_uiState.value.step) {
            ChangePasswordStep.VerifyCurrent -> {
                if (currentLength >= PasswordPolicy.MAX_LENGTH) return
                currentPassword[currentLength++] = digit.digitToChar()
                _uiState.update { it.copy(currentPasswordLength = currentLength, error = null) }
            }
            ChangePasswordStep.CreateNew -> {
                if (!currentPasswordVerified || newLength >= PasswordPolicy.MAX_LENGTH) return
                newPassword[newLength++] = digit.digitToChar()
                _uiState.update { it.copy(newPasswordLength = newLength, error = null) }
            }
            ChangePasswordStep.ConfirmNew -> {
                if (!currentPasswordVerified || confirmationLength >= PasswordPolicy.MAX_LENGTH) {
                    return
                }
                confirmation[confirmationLength++] = digit.digitToChar()
                _uiState.update {
                    it.copy(confirmPasswordLength = confirmationLength, error = null)
                }
            }
            ChangePasswordStep.Completed -> Unit
        }
    }

    fun deleteDigit() {
        if (_uiState.value.isProcessing || _uiState.value.showSamePasswordPrompt) return
        when (_uiState.value.step) {
            ChangePasswordStep.VerifyCurrent -> {
                if (currentLength == 0) return
                currentPassword[--currentLength] = NULL_CHAR
                _uiState.update { it.copy(currentPasswordLength = currentLength, error = null) }
            }
            ChangePasswordStep.CreateNew -> {
                if (newLength == 0) return
                newPassword[--newLength] = NULL_CHAR
                _uiState.update { it.copy(newPasswordLength = newLength, error = null) }
            }
            ChangePasswordStep.ConfirmNew -> {
                if (confirmationLength == 0) return
                confirmation[--confirmationLength] = NULL_CHAR
                _uiState.update {
                    it.copy(confirmPasswordLength = confirmationLength, error = null)
                }
            }
            ChangePasswordStep.Completed -> Unit
        }
    }

    fun submit() {
        when (_uiState.value.step) {
            ChangePasswordStep.VerifyCurrent -> verifyCurrentPassword()
            ChangePasswordStep.CreateNew -> continueWithNewPassword()
            ChangePasswordStep.ConfirmNew -> saveNewPassword()
            ChangePasswordStep.Completed -> Unit
        }
    }

    private fun verifyCurrentPassword() {
        if (_uiState.value.isProcessing || currentLength !in VALID_LENGTH) return
        val passwordCopy = currentPassword.copyOf(currentLength)
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.verifyCurrentPassword(passwordCopy)) {
                is ChangePasswordResult.Success -> {
                    currentPasswordVerified = true
                    _uiState.value = ChangePasswordUiState(step = ChangePasswordStep.CreateNew)
                }
                is ChangePasswordResult.Failure -> {
                    clearCurrentPassword()
                    _uiState.value = ChangePasswordUiState(
                        step = ChangePasswordStep.VerifyCurrent,
                        error = result.error.toUiError(),
                    )
                }
            }
        }
    }

    private fun continueWithNewPassword() {
        if (
            !currentPasswordVerified ||
            _uiState.value.isProcessing ||
            newLength !in VALID_LENGTH
        ) {
            return
        }
        if (passwordsEqual(currentPassword, currentLength, newPassword, newLength)) {
            _uiState.update { it.copy(showSamePasswordPrompt = true, error = null) }
            return
        }
        clearCurrentPassword()
        moveToConfirmation()
    }

    fun resetSamePassword() {
        if (!_uiState.value.showSamePasswordPrompt) return
        clearNewPassword()
        _uiState.update {
            it.copy(
                newPasswordLength = 0,
                showSamePasswordPrompt = false,
                error = null,
            )
        }
    }

    fun acceptSamePassword() {
        if (!_uiState.value.showSamePasswordPrompt || !currentPasswordVerified) return
        clearCurrentPassword()
        moveToConfirmation()
    }

    private fun moveToConfirmation() {
        clearConfirmation()
        _uiState.value = ChangePasswordUiState(
            step = ChangePasswordStep.ConfirmNew,
            newPasswordLength = newLength,
        )
    }

    private fun saveNewPassword() {
        if (
            !currentPasswordVerified ||
            _uiState.value.isProcessing ||
            confirmationLength !in VALID_LENGTH
        ) {
            return
        }
        if (!passwordsEqual(newPassword, newLength, confirmation, confirmationLength)) {
            clearConfirmation()
            _uiState.update {
                it.copy(
                    confirmPasswordLength = 0,
                    error = ChangePasswordError.PasswordMismatch,
                )
            }
            return
        }

        val passwordCopy = newPassword.copyOf(newLength)
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.replacePassword(passwordCopy)) {
                is ChangePasswordResult.Success -> {
                    clearSensitiveInput()
                    _uiState.value = ChangePasswordUiState(step = ChangePasswordStep.Completed)
                }
                is ChangePasswordResult.Failure -> {
                    _uiState.update {
                        it.copy(isProcessing = false, error = result.error.toUiError())
                    }
                }
            }
        }
    }

    fun returnToPreviousStep(): Boolean {
        if (_uiState.value.isProcessing) return true
        return when (_uiState.value.step) {
            ChangePasswordStep.ConfirmNew -> {
                clearNewPassword()
                clearConfirmation()
                _uiState.value = ChangePasswordUiState(step = ChangePasswordStep.CreateNew)
                true
            }
            ChangePasswordStep.CreateNew,
            ChangePasswordStep.VerifyCurrent,
            ChangePasswordStep.Completed,
            -> false
        }
    }

    fun cancelFlow() {
        clearSensitiveInput()
        _uiState.value = ChangePasswordUiState()
    }

    private fun clearCurrentPassword() {
        currentPassword.fill(NULL_CHAR)
        currentLength = 0
    }

    private fun clearNewPassword() {
        newPassword.fill(NULL_CHAR)
        newLength = 0
    }

    private fun clearConfirmation() {
        confirmation.fill(NULL_CHAR)
        confirmationLength = 0
    }

    private fun clearSensitiveInput() {
        clearCurrentPassword()
        clearNewPassword()
        clearConfirmation()
        currentPasswordVerified = false
    }

    override fun onCleared() {
        clearSensitiveInput()
        super.onCleared()
    }

    private fun passwordsEqual(
        first: CharArray,
        firstLength: Int,
        second: CharArray,
        secondLength: Int,
    ): Boolean {
        var difference = firstLength xor secondLength
        for (index in first.indices) {
            difference = difference or (first[index].code xor second[index].code)
        }
        return difference == 0
    }

    private fun ChangePasswordFailure.toUiError(): ChangePasswordError = when (this) {
        ChangePasswordFailure.InvalidLength,
        ChangePasswordFailure.NonNumeric,
        -> ChangePasswordError.InvalidLength
        ChangePasswordFailure.CurrentPasswordIncorrect ->
            ChangePasswordError.CurrentPasswordIncorrect
        ChangePasswordFailure.SecurityDataInvalid -> ChangePasswordError.SecurityDataInvalid
        ChangePasswordFailure.VerificationFailed -> ChangePasswordError.VerificationFailed
        ChangePasswordFailure.HashFailed -> ChangePasswordError.HashFailed
        ChangePasswordFailure.SaveFailed -> ChangePasswordError.SaveFailed
    }

    class Factory(
        private val repository: ChangePasswordRepositoryContract,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChangePasswordViewModel::class.java))
            return ChangePasswordViewModel(repository) as T
        }
    }

    private companion object {
        val VALID_LENGTH = PasswordPolicy.MIN_LENGTH..PasswordPolicy.MAX_LENGTH
        const val NULL_CHAR = '\u0000'
    }
}
