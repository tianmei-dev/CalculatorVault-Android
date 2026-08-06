package com.aurora.calculatorvault.ui.message

enum class AppMessageType {
    Success,
    Info,
    Warning,
    Error,
}

data class AppMessage(
    val message: String,
    val type: AppMessageType = AppMessageType.Info,
)
