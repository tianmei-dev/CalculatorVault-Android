package com.aurora.calculatorvault.feature.disguise.shortcut

data class DisguiseShortcutIntentPayload(
    val action: String?,
    val shortcutId: String?,
    val extraKeys: Set<String>,
    val isExplicit: Boolean,
    val componentClassName: String?,
)

sealed interface DisguiseShortcutIntentResult {
    data class Valid(val shortcutId: String) : DisguiseShortcutIntentResult
    data object Invalid : DisguiseShortcutIntentResult
}

object DisguiseShortcutIdValidator {
    private val pattern = Regex(
        "^cv_disguise_[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    )

    fun isValid(value: String?): Boolean =
        value != null && value.length == EXPECTED_LENGTH && pattern.matches(value)

    private const val EXPECTED_LENGTH = 48
}

class DisguiseShortcutIntentParser(
    private val expectedComponentClassName: String,
) {
    fun parse(payload: DisguiseShortcutIntentPayload): DisguiseShortcutIntentResult {
        val shortcutId = payload.shortcutId
        val isValid = payload.isExplicit &&
            payload.componentClassName == expectedComponentClassName &&
            payload.action == DisguiseShortcutContract.ACTION_OPEN_DISGUISE_SHORTCUT &&
            payload.extraKeys == setOf(DisguiseShortcutContract.EXTRA_SHORTCUT_ID) &&
            DisguiseShortcutIdValidator.isValid(shortcutId)
        return if (isValid) {
            DisguiseShortcutIntentResult.Valid(requireNotNull(shortcutId))
        } else {
            DisguiseShortcutIntentResult.Invalid
        }
    }
}
