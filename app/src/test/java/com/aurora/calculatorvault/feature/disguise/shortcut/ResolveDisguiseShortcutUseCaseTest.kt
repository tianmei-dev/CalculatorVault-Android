package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveDisguiseShortcutUseCaseTest {
    @Test
    fun validExistingAvailableEntry_isReady() = runTest {
        val useCase = ResolveDisguiseShortcutUseCase(FakeDisguiseRepository(), FakeRuntime())
        assertEquals(ResolveDisguiseShortcutResult.Ready, useCase(TEST_SHORTCUT_ID))
    }

    @Test
    fun malformedOrMissingEntry_isRejected() = runTest {
        val repository = FakeDisguiseRepository(entry = null)
        val useCase = ResolveDisguiseShortcutUseCase(repository, FakeRuntime())
        assertEquals(ResolveDisguiseShortcutResult.InvalidShortcutId, useCase("invalid"))
        assertEquals(ResolveDisguiseShortcutResult.EntryNotFound, useCase(TEST_SHORTCUT_ID))
    }

    @Test
    fun unavailableStates_areMappedWithoutEnteringPassword() = runTest {
        val runtime = FakeRuntime()
        val useCase = ResolveDisguiseShortcutUseCase(FakeDisguiseRepository(), runtime)
        runtime.availability = InstalledAppAvailability.NotInstalled
        assertEquals(ResolveDisguiseShortcutResult.TargetNotInstalled, useCase(TEST_SHORTCUT_ID))
        runtime.availability = InstalledAppAvailability.Disabled
        assertEquals(ResolveDisguiseShortcutResult.TargetDisabled, useCase(TEST_SHORTCUT_ID))
        runtime.availability = InstalledAppAvailability.NoLauncher
        assertEquals(ResolveDisguiseShortcutResult.NoLaunchIntent, useCase(TEST_SHORTCUT_ID))
        runtime.availability = InstalledAppAvailability.Unknown
        assertEquals(ResolveDisguiseShortcutResult.Failed, useCase(TEST_SHORTCUT_ID))
    }
}
