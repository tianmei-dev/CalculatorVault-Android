package com.aurora.calculatorvault.feature.appmanagement.presentation

data class AppManagementUiState(
    val privateAppCount: Int = 0,
    val disguiseEntryCount: Int = 0,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isDisguiseLoading: Boolean = true,
    val disguiseLoadFailed: Boolean = false,
)
