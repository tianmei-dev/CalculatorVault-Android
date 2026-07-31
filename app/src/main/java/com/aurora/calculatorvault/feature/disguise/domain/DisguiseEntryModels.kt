package com.aurora.calculatorvault.feature.disguise.domain

data class DisguiseEntry(
    val id: Long,
    val packageName: String,
    val targetAppName: String,
    val customName: String,
    val iconId: DisguiseIconId,
    val createdAt: Long,
    val updatedAt: Long,
    val shortcutId: String? = null,
    val shortcutRequestState: ShortcutRequestState = ShortcutRequestState.NotRequested,
    val shortcutRequestedAt: Long? = null,
    val shortcutCallbackAt: Long? = null,
    val shortcutLastError: ShortcutRequestError? = null,
)

enum class ShortcutRequestState {
    NotRequested,
    RequestSubmitted,
    LauncherAccepted,
    Unsupported,
    Failed,
}

enum class ShortcutRequestError {
    InvalidConfiguration,
    IconGenerationFailed,
    RequestRejected,
    SecurityBlocked,
    StateSaveFailed,
    Unknown,
}

enum class DisguiseIconId {
    Files,
    Photos,
    Browser,
    Settings,
    Video,
    Music,
    Tools,
    Weather,
    Calendar,
    Calculator,
}

enum class DisguiseSortMode {
    CreatedNewest,
    UpdatedNewest,
    Name,
}

object DisguiseNamePolicy {
    const val MAX_LENGTH = 20

    fun normalize(value: String): String = value.take(MAX_LENGTH)

    fun isValid(value: String): Boolean = value.isNotBlank() && value.length <= MAX_LENGTH
}
