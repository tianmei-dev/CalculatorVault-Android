package com.aurora.calculatorvault.feature.appmanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppManagementViewModel(
    hiddenAppRepository: HiddenAppRepositoryContract,
    disguiseRepository: DisguiseEntryRepositoryContract,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppManagementUiState())
    val uiState: StateFlow<AppManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            hiddenAppRepository.observeHiddenApps()
                .catch {
                    _uiState.update { state ->
                        state.copy(isLoading = false, loadFailed = true)
                    }
                }
                .collect { apps ->
                    _uiState.update {
                        it.copy(
                            privateAppCount = apps.size,
                            isLoading = false,
                            loadFailed = false,
                        )
                    }
                }
        }
        viewModelScope.launch {
            disguiseRepository.observeEntries()
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            isDisguiseLoading = false,
                            disguiseLoadFailed = true,
                        )
                    }
                }
                .collect { entries ->
                    _uiState.update {
                        it.copy(
                            disguiseEntryCount = entries.size,
                            isDisguiseLoading = false,
                            disguiseLoadFailed = false,
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: HiddenAppRepositoryContract,
        private val disguiseRepository: DisguiseEntryRepositoryContract,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppManagementViewModel::class.java))
            return AppManagementViewModel(repository, disguiseRepository) as T
        }
    }
}
