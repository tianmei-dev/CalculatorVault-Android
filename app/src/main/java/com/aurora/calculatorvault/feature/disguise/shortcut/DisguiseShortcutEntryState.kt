package com.aurora.calculatorvault.feature.disguise.shortcut

sealed interface DisguiseShortcutEntryState {
    data object Resolving : DisguiseShortcutEntryState
    data object InvalidRequest : DisguiseShortcutEntryState
    data object ConfigurationMissing : DisguiseShortcutEntryState
    data object TargetNotInstalled : DisguiseShortcutEntryState
    data object TargetDisabled : DisguiseShortcutEntryState
    data object NoLaunchIntent : DisguiseShortcutEntryState
    data class AwaitingPassword(
        val enteredLength: Int = 0,
        val passwordIncorrect: Boolean = false,
    ) : DisguiseShortcutEntryState
    data object VerifyingPassword : DisguiseShortcutEntryState
    data object LaunchingTarget : DisguiseShortcutEntryState
    data class LaunchFailed(val reason: LaunchFailureReason) : DisguiseShortcutEntryState
    data object SessionExpired : DisguiseShortcutEntryState
}

enum class LaunchFailureReason {
    ActivityNotFound,
    SecurityBlocked,
    Unknown,
}

sealed interface DisguiseShortcutEntryEffect {
    data object Finish : DisguiseShortcutEntryEffect
    data object OpenCalculator : DisguiseShortcutEntryEffect
}
