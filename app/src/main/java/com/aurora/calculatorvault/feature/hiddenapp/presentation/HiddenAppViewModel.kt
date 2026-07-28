package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HiddenAppViewModel(
    private val repository: HiddenAppRepositoryContract,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HiddenAppUiState())
    val uiState: StateFlow<HiddenAppUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HiddenAppEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeApps()
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun retryLoad() {
        if (_uiState.value.error == HiddenAppError.LoadFailed) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            observeApps()
        }
    }

    fun requestRemoval(app: HiddenApp) {
        if (!_uiState.value.isRemoving) {
            _uiState.update { it.copy(pendingRemoval = app, error = null) }
        }
    }

    fun cancelRemoval() {
        _uiState.update { it.copy(pendingRemoval = null) }
    }

    fun confirmRemoval() {
        val app = _uiState.value.pendingRemoval ?: return
        if (_uiState.value.isRemoving) return
        _uiState.update { it.copy(isRemoving = true, error = null) }
        viewModelScope.launch {
            try {
                if (!repository.removeApp(app.packageName)) {
                    _uiState.update {
                        it.copy(isRemoving = false, error = HiddenAppError.RemoveFailed)
                    }
                    return@launch
                }
                _uiState.update { it.copy(isRemoving = false, pendingRemoval = null) }
                _effects.send(HiddenAppEffect.Removed)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isRemoving = false, error = HiddenAppError.RemoveFailed)
                }
            }
        }
    }

    private fun observeApps() {
        viewModelScope.launch {
            try {
                repository.observeHiddenApps().collect { apps ->
                    _uiState.update { it.copy(isLoading = false, apps = apps) }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = HiddenAppError.LoadFailed)
                }
            }
        }
    }

    class Factory(
        private val repository: HiddenAppRepositoryContract,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HiddenAppViewModel::class.java))
            return HiddenAppViewModel(repository) as T
        }
    }
}
