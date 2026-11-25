package com.aurora.calculatorvault.feature.calculator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryRepository
import com.aurora.calculatorvault.core.security.recovery.PasswordRecoveryResult
import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorAction
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorEngine
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorState
import com.aurora.calculatorvault.feature.calculator.domain.VaultUnlockUseCase
import com.aurora.calculatorvault.feature.calculator.domain.isDirectUnlockInputState
import com.aurora.calculatorvault.feature.calculator.domain.isUnlockCandidate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CalculatorViewModel(
    private val engine: CalculatorEngine = CalculatorEngine(),
    private val unlockUseCase: VaultUnlockUseCase? = null,
    private val sessionManager: VaultSessionManager? = null,
    private val passwordRecoveryRepository: PasswordRecoveryRepository? = null,
    val isBasicMode: Boolean = false,
    private val hasPrivacyConsent: () -> Boolean = { true },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CalculatorEffect>(capacity = Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val _passwordRevealState =
        MutableStateFlow<CalculatorPasswordRevealState>(CalculatorPasswordRevealState.Hidden)
    val passwordRevealState: StateFlow<CalculatorPasswordRevealState> =
        _passwordRevealState.asStateFlow()

    private val initialLockGeneration = sessionManager?.lockGeneration?.value

    // 仅保存在 ViewModel 内存中，不进入公开 UiState、持久化层或日志。
    private val unlockCandidateInput = CharArray(MAX_UNLOCK_CANDIDATE_LENGTH)
    private var unlockCandidateLength = 0
    private var unlockCandidateEligible = true
    private var unlockJobActive = false

    init {
        if (!isBasicMode && sessionManager != null) {
            viewModelScope.launch {
                var observedGeneration = initialLockGeneration ?: sessionManager.lockGeneration.value
                sessionManager.lockGeneration.collect { generation ->
                    if (generation != observedGeneration) {
                        observedGeneration = generation
                        unlockJobActive = false
                        clearUnlockCandidate(allowNewRound = true)
                        dismissPasswordReveal()
                        _uiState.value = CalculatorState()
                    }
                }
            }
        }
    }

    fun onAction(action: CalculatorAction) {
        if (!canAccessVault()) {
            _uiState.update { state -> engine.reduce(state, action) }
            return
        }
        if (unlockJobActive) return
        val currentState = _uiState.value
        when (action) {
            is CalculatorAction.Number -> recordUnlockDigit(currentState, action.value)
            CalculatorAction.Delete -> deleteUnlockDigit(currentState)
            CalculatorAction.Clear -> clearUnlockCandidate(allowNewRound = true)
            CalculatorAction.Decimal,
            CalculatorAction.ToggleSign,
            CalculatorAction.Percent,
            is CalculatorAction.Operator,
            -> clearUnlockCandidate(allowNewRound = false)

            CalculatorAction.Equals -> {
                if (tryCheckUnlock()) return
                clearUnlockCandidate(allowNewRound = false)
            }
        }
        _uiState.update { state -> engine.reduce(state, action) }
    }

    fun revealCurrentPassword() {
        if (!canAccessVault()) return
        if (_passwordRevealState.value is CalculatorPasswordRevealState.Visible) return
        val repository = passwordRecoveryRepository ?: run {
            _passwordRevealState.value = CalculatorPasswordRevealState.Unavailable
            return
        }
        viewModelScope.launch {
            if (!canAccessVault()) return@launch
            dismissPasswordReveal()
            val result = repository.reveal()
            if (!canAccessVault()) {
                (result as? PasswordRecoveryResult.Success)?.password?.fill(NULL_CHAR)
                return@launch
            }
            _passwordRevealState.value = when (result) {
                is PasswordRecoveryResult.Success ->
                    CalculatorPasswordRevealState.Visible(result.password)
                PasswordRecoveryResult.Unavailable,
                PasswordRecoveryResult.Corrupted,
                -> CalculatorPasswordRevealState.Unavailable
                PasswordRecoveryResult.Failed -> CalculatorPasswordRevealState.Failed
            }
        }
    }

    fun dismissPasswordReveal() {
        (_passwordRevealState.value as? CalculatorPasswordRevealState.Visible)
            ?.password
            ?.fill(NULL_CHAR)
        _passwordRevealState.value = CalculatorPasswordRevealState.Hidden
    }

    private fun tryCheckUnlock(): Boolean {
        val useCase = unlockUseCase ?: return false
        val manager = sessionManager ?: return false
        val candidateState = _uiState.value
        if (!unlockCandidateEligible) return false
        val candidate = unlockCandidateInput.copyOf(unlockCandidateLength)
        if (!isUnlockCandidate(candidateState, candidate)) {
            candidate.fill(NULL_CHAR)
            return false
        }
        val recoveryCandidate = candidate.copyOf()

        clearUnlockCandidate(allowNewRound = false)
        unlockJobActive = true
        viewModelScope.launch {
            try {
                val verified = if (canAccessVault()) {
                    useCase.verify(candidate)
                } else {
                    candidate.fill(NULL_CHAR)
                    false
                }
                if (_uiState.value == candidateState && verified && canAccessVault() && manager.tryUnlock()) {
                    clearUnlockCandidate(allowNewRound = true)
                    _uiState.value = CalculatorState()
                    _effects.send(CalculatorEffect.OpenVault)
                    maybeBackfillRecoveryMaterial(recoveryCandidate)
                } else if (_uiState.value == candidateState) {
                    recoveryCandidate.fill(NULL_CHAR)
                    _uiState.value = engine.reduce(candidateState, CalculatorAction.Equals)
                } else {
                    recoveryCandidate.fill(NULL_CHAR)
                }
            } catch (_: Exception) {
                recoveryCandidate.fill(NULL_CHAR)
                if (_uiState.value == candidateState) {
                    _uiState.value = engine.reduce(candidateState, CalculatorAction.Equals)
                }
            } finally {
                unlockJobActive = false
            }
        }
        return true
    }

    private suspend fun maybeBackfillRecoveryMaterial(candidate: CharArray) {
        val repository = passwordRecoveryRepository ?: run {
            candidate.fill(NULL_CHAR)
            return
        }
        try {
            withTimeoutOrNull(RECOVERY_BACKFILL_TIMEOUT_MS) {
                if (canAccessVault() && !repository.hasMaterial() && canAccessVault()) {
                    repository.store(candidate)
                } else {
                    candidate.fill(NULL_CHAR)
                }
            } ?: candidate.fill(NULL_CHAR)
        } catch (_: Exception) {
            candidate.fill(NULL_CHAR)
        }
    }

    private fun canAccessVault(): Boolean = !isBasicMode && hasPrivacyConsent()

    private fun recordUnlockDigit(
        state: CalculatorState,
        digit: Int,
    ) {
        val startsNewRound = state.error != null ||
            (state.isResultShown && state.pendingOperator == null)
        if (startsNewRound) {
            clearUnlockCandidate(allowNewRound = true)
        }
        if (!unlockCandidateEligible) return
        if (!startsNewRound && !isDirectUnlockInputState(state)) {
            clearUnlockCandidate(allowNewRound = false)
            return
        }
        if (unlockCandidateLength == MAX_UNLOCK_CANDIDATE_LENGTH) {
            clearUnlockCandidate(allowNewRound = false)
            return
        }
        unlockCandidateInput[unlockCandidateLength] = digit.digitToChar()
        unlockCandidateLength += 1
    }

    private fun deleteUnlockDigit(state: CalculatorState) {
        if (state.error != null || state.isResultShown) {
            clearUnlockCandidate(allowNewRound = true)
            return
        }
        if (!unlockCandidateEligible || unlockCandidateLength == 0) {
            if (isDirectUnlockInputState(state) && state.displayValue.willDeleteToZero()) {
                clearUnlockCandidate(allowNewRound = true)
            }
            return
        }
        unlockCandidateLength -= 1
        unlockCandidateInput[unlockCandidateLength] = NULL_CHAR
    }

    private fun clearUnlockCandidate(allowNewRound: Boolean) {
        unlockCandidateInput.fill(NULL_CHAR)
        unlockCandidateLength = 0
        unlockCandidateEligible = allowNewRound
    }

    override fun onCleared() {
        clearUnlockCandidate(allowNewRound = false)
        dismissPasswordReveal()
        _effects.close()
        super.onCleared()
    }

    private fun String.willDeleteToZero(): Boolean {
        val next = dropLast(1)
        return next.isEmpty() || next == "-"
    }

    class Factory(
        private val unlockUseCase: VaultUnlockUseCase,
        private val sessionManager: VaultSessionManager,
        private val passwordRecoveryRepository: PasswordRecoveryRepository,
        private val hasPrivacyConsent: () -> Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CalculatorViewModel::class.java))
            return CalculatorViewModel(
                unlockUseCase = unlockUseCase,
                sessionManager = sessionManager,
                passwordRecoveryRepository = passwordRecoveryRepository,
                hasPrivacyConsent = hasPrivacyConsent,
            ) as T
        }
    }

    object BasicFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CalculatorViewModel::class.java))
            return CalculatorViewModel(isBasicMode = true) as T
        }
    }

    private companion object {
        const val MAX_UNLOCK_CANDIDATE_LENGTH = 8
        const val RECOVERY_BACKFILL_TIMEOUT_MS = 800L
        const val NULL_CHAR = '\u0000'
    }
}
