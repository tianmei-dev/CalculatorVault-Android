package com.aurora.calculatorvault.ui.message

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class AppMessageController {
    private val _messages = MutableSharedFlow<AppMessage>(
        extraBufferCapacity = MESSAGE_BUFFER_CAPACITY,
    )
    val messages: SharedFlow<AppMessage> = _messages

    fun showSuccess(message: String) {
        show(AppMessage(message = message, type = AppMessageType.Success))
    }

    fun showInfo(message: String) {
        show(AppMessage(message = message, type = AppMessageType.Info))
    }

    fun showWarning(message: String) {
        show(AppMessage(message = message, type = AppMessageType.Warning))
    }

    fun showError(message: String) {
        show(AppMessage(message = message, type = AppMessageType.Error))
    }

    fun show(message: AppMessage) {
        _messages.tryEmit(message)
    }

    private companion object {
        const val MESSAGE_BUFFER_CAPACITY = 32
    }
}

val LocalAppMessageController = staticCompositionLocalOf<AppMessageController> {
    error("AppMessageController is not provided")
}
