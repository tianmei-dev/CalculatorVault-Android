package com.aurora.calculatorvault.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecentHistoryUiState(
    val hasHistory: Boolean = false,
    val showConfirmation: Boolean = false,
    val isClearing: Boolean = false,
    val clearFailed: Boolean = false,
)

class RecentHistoryViewModel(
    private val repository: HiddenAppRepositoryContract,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecentHistoryUiState())
    val uiState: StateFlow<RecentHistoryUiState> = _uiState.asStateFlow()

    private val _cleared = Channel<Unit>(Channel.BUFFERED)
    val cleared = _cleared.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeRecentApps(1).collect { apps ->
                _uiState.update { it.copy(hasHistory = apps.isNotEmpty()) }
            }
        }
    }

    fun requestClear() {
        if (_uiState.value.hasHistory && !_uiState.value.isClearing) {
            _uiState.update { it.copy(showConfirmation = true, clearFailed = false) }
        }
    }

    fun cancelClear() {
        _uiState.update { it.copy(showConfirmation = false) }
    }

    fun confirmClear() {
        if (!_uiState.value.showConfirmation || _uiState.value.isClearing) return
        _uiState.update { it.copy(isClearing = true, clearFailed = false) }
        viewModelScope.launch {
            try {
                repository.clearRecentHistory()
                _uiState.update {
                    it.copy(showConfirmation = false, isClearing = false, clearFailed = false)
                }
                _cleared.send(Unit)
            } catch (_: Exception) {
                _uiState.update { it.copy(isClearing = false, clearFailed = true) }
            }
        }
    }

    class Factory(
        private val repository: HiddenAppRepositoryContract,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecentHistoryViewModel::class.java))
            return RecentHistoryViewModel(repository) as T
        }
    }
}
