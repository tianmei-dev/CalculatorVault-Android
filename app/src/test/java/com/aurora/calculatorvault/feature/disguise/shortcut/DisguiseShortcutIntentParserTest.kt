package com.aurora.calculatorvault.feature.disguise.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisguiseShortcutIntentParserTest {
    private val component = DisguiseShortcutEntryActivity::class.java.name
    private val parser = DisguiseShortcutIntentParser(component)
    private val validId = "cv_disguise_123e4567-e89b-42d3-a456-426614174000"

    @Test
    fun validExplicitIntent_isAccepted() {
        assertEquals(
            DisguiseShortcutIntentResult.Valid(validId),
            parser.parse(payload()),
        )
    }

    @Test
    fun missingOrMalformedId_isRejected() {
        assertEquals(DisguiseShortcutIntentResult.Invalid, parser.parse(payload(id = null)))
        assertEquals(DisguiseShortcutIntentResult.Invalid, parser.parse(payload(id = "cv_disguise_bad")))
        assertFalse(DisguiseShortcutIdValidator.isValid("cv_disguise_123\n"))
    }

    @Test
    fun wrongActionOrImplicitIntent_isRejected() {
        assertEquals(DisguiseShortcutIntentResult.Invalid, parser.parse(payload(action = "other")))
        assertEquals(DisguiseShortcutIntentResult.Invalid, parser.parse(payload(explicit = false)))
    }

    @Test
    fun wrongComponentOrAdditionalExtra_isRejected() {
        assertEquals(DisguiseShortcutIntentResult.Invalid, parser.parse(payload(component = "other.Activity")))
        assertEquals(
            DisguiseShortcutIntentResult.Invalid,
            parser.parse(payload(keys = setOf(DisguiseShortcutContract.EXTRA_SHORTCUT_ID, "targetPackage"))),
        )
    }

    @Test
    fun generatedUuidShape_isStrictlyValidated() {
        assertTrue(DisguiseShortcutIdValidator.isValid(validId))
        assertFalse(
            DisguiseShortcutIdValidator.isValid(
                "cv_disguise_123e4567-e89b-02d3-a456-426614174000",
            ),
        )
    }

    private fun payload(
        action: String? = DisguiseShortcutContract.ACTION_OPEN_DISGUISE_SHORTCUT,
        id: String? = validId,
        keys: Set<String> = setOf(DisguiseShortcutContract.EXTRA_SHORTCUT_ID),
        explicit: Boolean = true,
        component: String? = this.component,
    ) = DisguiseShortcutIntentPayload(action, id, keys, explicit, component)
}
