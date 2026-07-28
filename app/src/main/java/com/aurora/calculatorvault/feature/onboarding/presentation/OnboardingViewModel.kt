package com.aurora.calculatorvault.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingFailure
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingRepositoryContract
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingResult
import com.aurora.calculatorvault.feature.onboarding.domain.PasswordPolicy
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: OnboardingRepositoryContract,
) : ViewModel() {

    private val password = CharArray(PasswordPolicy.MAX_LENGTH)
    private val confirmation = CharArray(PasswordPolicy.MAX_LENGTH)
    private var passwordLength = 0
    private var confirmationLength = 0

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadStartupDestination()
    }

    fun loadStartupDestination() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(step = OnboardingStep.Loading, isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.resolveStartupDestination()) {
                is OnboardingResult.Success -> {
                    val step = when (result.value) {
                        StartupDestination.PrivacyConsent -> OnboardingStep.PrivacyConsent
                        StartupDestination.CreatePassword -> OnboardingStep.CreatePassword
                        StartupDestination.Calculator -> OnboardingStep.Calculator
                    }
                    _uiState.update { it.copy(step = step, isSaving = false, error = null) }
                }

                is OnboardingResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            step = OnboardingStep.Loading,
                            isSaving = false,
                            error = OnboardingError.StartupReadFailed,
                        )
                    }
                }
            }
        }
    }

    fun acceptPrivacy() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.acceptPrivacy()) {
                is OnboardingResult.Success -> {
                    clearSensitiveInput()
                    _uiState.value = OnboardingUiState(step = OnboardingStep.CreatePassword)
                }

                is OnboardingResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = result.error.toUiError(),
                        )
                    }
                }
            }
        }
    }

    fun addPasswordDigit(digit: Int) {
        if (
            _uiState.value.step != OnboardingStep.CreatePassword ||
            digit !in 0..9 ||
            passwordLength >= PasswordPolicy.MAX_LENGTH ||
            _uiState.value.isSaving
        ) {
            return
        }
        password[passwordLength++] = digit.digitToChar()
        _uiState.update {
            it.copy(passwordLength = passwordLength, error = null)
        }
    }

    fun deletePasswordDigit() {
        if (
            _uiState.value.step != OnboardingStep.CreatePassword ||
            passwordLength == 0 ||
            _uiState.value.isSaving
        ) {
            return
        }
        password[--passwordLength] = NULL_CHAR
        _uiState.update { it.copy(passwordLength = passwordLength, error = null) }
    }

    fun continueToConfirmation() {
        if (_uiState.value.step != OnboardingStep.CreatePassword || _uiState.value.isSaving) return
        if (passwordLength !in PasswordPolicy.MIN_LENGTH..PasswordPolicy.MAX_LENGTH) {
            _uiState.update { it.copy(error = OnboardingError.PasswordTooShort) }
            return
        }
        clearConfirmation()
        _uiState.update {
            it.copy(
                step = OnboardingStep.ConfirmPassword,
                confirmPasswordLength = 0,
                error = null,
            )
        }
    }

    fun addConfirmationDigit(digit: Int) {
        if (
            digit !in 0..9 ||
            _uiState.value.step != OnboardingStep.ConfirmPassword ||
            confirmationLength >= PasswordPolicy.MAX_LENGTH ||
            _uiState.value.isSaving
        ) {
            return
        }
        confirmation[confirmationLength++] = digit.digitToChar()
        _uiState.update {
            it.copy(confirmPasswordLength = confirmationLength, error = null)
        }
    }

    fun deleteConfirmationDigit() {
        if (
            _uiState.value.step != OnboardingStep.ConfirmPassword ||
            confirmationLength == 0 ||
            _uiState.value.isSaving
        ) {
            return
        }
        confirmation[--confirmationLength] = NULL_CHAR
        _uiState.update { it.copy(confirmPasswordLength = confirmationLength, error = null) }
    }

    fun confirmPassword() {
        if (_uiState.value.step != OnboardingStep.ConfirmPassword || _uiState.value.isSaving) return
        if (confirmationLength !in PasswordPolicy.MIN_LENGTH..PasswordPolicy.MAX_LENGTH) {
            _uiState.update { it.copy(error = OnboardingError.PasswordTooShort) }
            return
        }
        if (!passwordsMatch()) {
            clearSensitiveInput()
            _uiState.value = OnboardingUiState(
                step = OnboardingStep.CreatePassword,
                error = OnboardingError.PasswordMismatch,
            )
            return
        }

        val passwordCopy = password.copyOf(passwordLength)
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.configurePassword(passwordCopy)) {
                is OnboardingResult.Success -> {
                    clearSensitiveInput()
                    _uiState.value = OnboardingUiState(step = OnboardingStep.Calculator)
                }

                is OnboardingResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = result.error.toUiError(),
                        )
                    }
                }
            }
        }
    }

    fun returnToCreatePassword() {
        if (_uiState.value.isSaving) return
        clearSensitiveInput()
        _uiState.value = OnboardingUiState(step = OnboardingStep.CreatePassword)
    }

    private fun passwordsMatch(): Boolean {
        var difference = passwordLength xor confirmationLength
        for (index in password.indices) {
            difference = difference or (password[index].code xor confirmation[index].code)
        }
        return difference == 0
    }

    private fun clearConfirmation() {
        confirmation.fill(NULL_CHAR)
        confirmationLength = 0
    }

    private fun clearSensitiveInput() {
        password.fill(NULL_CHAR)
        confirmation.fill(NULL_CHAR)
        passwordLength = 0
        confirmationLength = 0
    }

    override fun onCleared() {
        clearSensitiveInput()
        super.onCleared()
    }

    private fun OnboardingFailure.toUiError(): OnboardingError = when (this) {
        OnboardingFailure.StartupReadFailed -> OnboardingError.StartupReadFailed
        OnboardingFailure.PrivacySaveFailed -> OnboardingError.PrivacySaveFailed
        OnboardingFailure.PasswordTooShort -> OnboardingError.PasswordTooShort
        OnboardingFailure.PasswordTooLong -> OnboardingError.PasswordTooLong
        OnboardingFailure.PasswordNonNumeric -> OnboardingError.PasswordNonNumeric
        OnboardingFailure.PasswordHashFailed -> OnboardingError.PasswordHashFailed
        OnboardingFailure.PasswordSaveFailed -> OnboardingError.PasswordSaveFailed
    }

    class Factory(
        private val repository: OnboardingRepositoryContract,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OnboardingViewModel::class.java))
            return OnboardingViewModel(repository) as T
        }
    }

    private companion object {
        const val NULL_CHAR = '\u0000'
    }
}
