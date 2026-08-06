package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutRepositoryContractTest {
    @Test
    fun `update request carries only shortcut display data`() = runTest {
        val request = ShortcutUpdateRequest(
            shortcutId = TEST_SHORTCUT_ID,
            displayName = "Files",
            iconId = DisguiseIconId.Files,
        )

        assertEquals(TEST_SHORTCUT_ID, request.shortcutId)
        assertEquals("Files", request.displayName)
        assertEquals(DisguiseIconId.Files, request.iconId)
        assertEquals(false, request.toString().contains(TEST_PACKAGE))
    }
}
