package com.aurora.calculatorvault.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {
    @Test
    fun `published event ids remain complete and valid`() {
        val expected = setOf(
            "app_disguise_click",
            "app_lock_click",
            "app_lock_enable",
            "private_album_click",
            "private_media_import_click",
            "private_folder_create_click",
            "private_media_restore_click",
            "change_password_click",
            "permission_settings_click",
            "privacy_documents_click",
        )

        assertEquals(expected, AnalyticsEvent.entries.map { it.id }.toSet())
        assertTrue(AnalyticsEvent.entries.all { it.id.matches(Regex("[a-z][a-z0-9_]*")) })
    }
}
