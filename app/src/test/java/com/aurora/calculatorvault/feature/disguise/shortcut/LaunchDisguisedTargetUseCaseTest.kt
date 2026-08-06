package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchDisguisedTargetUseCaseTest {
    @Test
    fun success_reReadsEntry_launchesAndUsesExistingRecentRecorder() = runTest {
        val runtime = FakeRuntime()
        val hiddenRepository = FakeHiddenRepository()
        val useCase = LaunchDisguisedTargetUseCase(
            FakeDisguiseRepository(),
            runtime,
            LaunchHiddenAppUseCase(runtime, hiddenRepository) { 123L },
        )
        assertEquals(LaunchDisguisedTargetResult.Success, useCase(TEST_SHORTCUT_ID))
        assertEquals(1, runtime.launchCalls)
        assertEquals(1, hiddenRepository.markOpenedCalls)
    }

    @Test
    fun configurationDeletedDuringVerification_doesNotLaunch() = runTest {
        val runtime = FakeRuntime()
        val useCase = createUseCase(FakeDisguiseRepository(null), runtime)
        assertEquals(LaunchDisguisedTargetResult.EntryMissing, useCase(TEST_SHORTCUT_ID))
        assertEquals(0, runtime.launchCalls)
    }

    @Test
    fun availabilityChangedDuringVerification_doesNotLaunch() = runTest {
        val runtime = FakeRuntime(InstalledAppAvailability.NotInstalled)
        val useCase = createUseCase(FakeDisguiseRepository(), runtime)
        assertEquals(LaunchDisguisedTargetResult.TargetNotInstalled, useCase(TEST_SHORTCUT_ID))
        runtime.availability = InstalledAppAvailability.Disabled
        assertEquals(LaunchDisguisedTargetResult.TargetDisabled, useCase(TEST_SHORTCUT_ID))
        runtime.availability = InstalledAppAvailability.NoLauncher
        assertEquals(LaunchDisguisedTargetResult.NoLaunchIntent, useCase(TEST_SHORTCUT_ID))
        assertEquals(0, runtime.launchCalls)
    }

    @Test
    fun launchExceptions_areMappedBySharedRuntimeResult() = runTest {
        val runtime = FakeRuntime()
        val useCase = createUseCase(FakeDisguiseRepository(), runtime)
        runtime.launchResult = AppLaunchResult.ActivityNotFound
        assertEquals(LaunchDisguisedTargetResult.ActivityNotFound, useCase(TEST_SHORTCUT_ID))
        runtime.launchResult = AppLaunchResult.SecurityBlocked
        assertEquals(LaunchDisguisedTargetResult.SecurityBlocked, useCase(TEST_SHORTCUT_ID))
        runtime.launchResult = AppLaunchResult.Failed
        assertEquals(LaunchDisguisedTargetResult.Failed, useCase(TEST_SHORTCUT_ID))
    }

    private fun createUseCase(repository: FakeDisguiseRepository, runtime: FakeRuntime) =
        LaunchDisguisedTargetUseCase(
            repository,
            runtime,
            LaunchHiddenAppUseCase(runtime, FakeHiddenRepository()),
        )
}
