package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutStatus
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutSyncManagerTest {
    @Test
    fun `available target with pinned shortcut is created`() = runTest {
        val shortcuts = FakeShortcutRepository(present = true)
        val manager = ShortcutSyncManager(shortcuts, FakeRuntime())

        assertEquals(
            ShortcutStatus.CREATED,
            manager.resolveStatus(
                testEntry().copy(
                    shortcutId = TEST_SHORTCUT_ID,
                    shortcutRequestState = ShortcutRequestState.LauncherAccepted,
                ),
            ),
        )
    }

    @Test
    fun `missing pinned shortcut after accepted request needs recreate`() = runTest {
        val shortcuts = FakeShortcutRepository(present = false)
        val manager = ShortcutSyncManager(shortcuts, FakeRuntime())

        assertEquals(
            ShortcutStatus.NEED_RECREATE,
            manager.resolveStatus(
                testEntry().copy(
                    shortcutId = TEST_SHORTCUT_ID,
                    shortcutRequestState = ShortcutRequestState.LauncherAccepted,
                ),
            ),
        )
    }

    @Test
    fun `never requested shortcut is not created`() = runTest {
        val manager = ShortcutSyncManager(FakeShortcutRepository(present = false), FakeRuntime())

        assertEquals(
            ShortcutStatus.NOT_CREATED,
            manager.resolveStatus(testEntry().copy(shortcutId = TEST_SHORTCUT_ID)),
        )
    }

    @Test
    fun `target availability has priority over shortcut presence`() = runTest {
        val runtime = FakeRuntime(availability = InstalledAppAvailability.NotInstalled)
        val manager = ShortcutSyncManager(FakeShortcutRepository(present = true), runtime)

        assertEquals(ShortcutStatus.TARGET_UNINSTALLED, manager.resolveStatus(testEntry()))
        runtime.availability = InstalledAppAvailability.Disabled
        assertEquals(ShortcutStatus.TARGET_DISABLED, manager.resolveStatus(testEntry()))
        runtime.availability = InstalledAppAvailability.NoLauncher
        assertEquals(ShortcutStatus.CONFIG_INVALID, manager.resolveStatus(testEntry()))
    }

    @Test
    fun `target reinstall automatically restores created state when shortcut still exists`() = runTest {
        val runtime = FakeRuntime(availability = InstalledAppAvailability.NotInstalled)
        val manager = ShortcutSyncManager(FakeShortcutRepository(present = true), runtime)
        val entry = testEntry().copy(
            shortcutId = TEST_SHORTCUT_ID,
            shortcutRequestState = ShortcutRequestState.LauncherAccepted,
        )

        assertEquals(ShortcutStatus.TARGET_UNINSTALLED, manager.resolveStatus(entry))
        runtime.availability = InstalledAppAvailability.Available
        assertEquals(ShortcutStatus.CREATED, manager.resolveStatus(entry))
    }

    private class FakeShortcutRepository(
        private val present: Boolean,
    ) : ShortcutRepository {
        override fun isPinRequestSupported() = true
        override suspend fun isShortcutPresent(shortcutId: String) = present
        override suspend fun update(request: ShortcutUpdateRequest) = ShortcutOperationResult.Success
        override suspend fun remove(shortcutId: String) = ShortcutOperationResult.Success
    }
}
