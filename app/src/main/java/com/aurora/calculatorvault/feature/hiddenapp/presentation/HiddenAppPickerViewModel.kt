package com.aurora.calculatorvault.feature.hiddenapp.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HiddenAppPickerViewModel(
    private val repository: HiddenAppRepositoryContract,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HiddenAppPickerUiState())
    val uiState: StateFlow<HiddenAppPickerUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HiddenAppPickerEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val selectionOrder = mutableListOf<String>()

    init {
        observeAddedPackages()
        scan()
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun toggleSelection(packageName: String) {
        val state = _uiState.value
        if (state.isSaving || packageName in state.addedPackages) return
        if (packageName in selectionOrder) {
            selectionOrder.remove(packageName)
        } else if (state.apps.any { it.packageName == packageName }) {
            selectionOrder.add(packageName)
        }
        publishSelection()
    }

    fun retryScan() {
        if (!_uiState.value.isLoading) scan()
    }

    fun saveSelection() {
        val state = _uiState.value
        if (!state.canSave) return
        val selected = selectionOrder.mapNotNull { packageName ->
            state.apps.firstOrNull { it.packageName == packageName }
        }
        if (selected.isEmpty()) return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val count = repository.addApps(selected)
                if (count <= 0) {
                    debugLog("Hidden app save completed without new records")
                    _uiState.update {
                        it.copy(isSaving = false, error = HiddenAppError.SaveFailed)
                    }
                    return@launch
                }
                selectionOrder.clear()
                publishSelection()
                _uiState.update { it.copy(isSaving = false) }
                _effects.send(HiddenAppPickerEffect.Completed(count))
            } catch (error: Exception) {
                debugLog("Hidden app save failed: ${error::class.java.simpleName}")
                _uiState.update {
                    it.copy(isSaving = false, error = HiddenAppError.SaveFailed)
                }
            }
        }
    }

    private fun scan() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val apps = repository.scanInstalledApps()
                _uiState.update { it.copy(isLoading = false, apps = apps) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = HiddenAppError.ScanFailed)
                }
            }
        }
    }

    private fun observeAddedPackages() {
        viewModelScope.launch {
            try {
                repository.observeAddedPackageNames().collect { added ->
                    selectionOrder.removeAll(added)
                    _uiState.update { it.copy(addedPackages = added) }
                    publishSelection()
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = HiddenAppError.LoadFailed) }
            }
        }
    }

    private fun publishSelection() {
        _uiState.update { it.copy(selectedPackages = selectionOrder.toSet()) }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG, message) }
        }
    }

    class Factory(
        private val repository: HiddenAppRepositoryContract,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HiddenAppPickerViewModel::class.java))
            return HiddenAppPickerViewModel(repository) as T
        }
    }

    private companion object {
        const val TAG = "HiddenAppPicker"
    }
}
