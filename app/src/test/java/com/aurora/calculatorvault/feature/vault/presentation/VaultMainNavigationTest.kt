package com.aurora.calculatorvault.feature.vault.presentation

import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMainNavigationTest {
    @Test
    fun `new vault session defaults to app management`() {
        assertEquals(VaultMainTab.AppManagement, VaultMainTab.default)
        assertEquals(VaultTabRoute.AppManagement, VaultMainTab.default.route)
    }

    @Test
    fun `main tabs have fixed product order`() {
        assertEquals(
            listOf(
                VaultMainTab.AppManagement,
                VaultMainTab.AppLock,
                VaultMainTab.PrivateAlbum,
                VaultMainTab.Settings,
            ),
            VaultMainTab.entries,
        )
    }

    @Test
    fun `private apps remains secondary and old first level pages are absent`() {
        val firstLevelRoutes = VaultMainTab.entries.map { it.route }

        assertFalse(VaultTabRoute.HiddenApp in firstLevelRoutes)
        assertFalse(VaultTabRoute.AppDisguise in firstLevelRoutes)
        assertTrue(VaultTabRoute.AppManagement in firstLevelRoutes)
        assertTrue(VaultTabRoute.AppLock in firstLevelRoutes)
    }

    @Test
    fun `tab routes and accessibility tags are unique`() {
        assertEquals(
            VaultMainTab.entries.size,
            VaultMainTab.entries.map { it.route.path }.distinct().size,
        )
        assertEquals(
            VaultMainTab.entries.size,
            VaultMainTab.entries.map { it.testTag }.distinct().size,
        )
    }
}
