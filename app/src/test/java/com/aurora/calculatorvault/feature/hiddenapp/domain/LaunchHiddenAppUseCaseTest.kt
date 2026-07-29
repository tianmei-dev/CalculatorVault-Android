package com.aurora.calculatorvault.feature.hiddenapp.domain

import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchHiddenAppUseCaseTest {

    @Test
    fun `success records opened time after launcher accepts request`() = runTest {
        val repository = FakeRepository()
        val useCase = LaunchHiddenAppUseCase(
            runtime = FakeRuntime(AppLaunchResult.Success),
            repository = repository,
            currentTimeMillis = { 42L },
        )

        assertEquals(AppLaunchResult.Success, useCase("target"))
        assertEquals(listOf("target" to 42L), repository.opened)
    }

    @Test
    fun `every launch failure leaves recent history unchanged`() = runTest {
        val failures = listOf(
            AppLaunchResult.InvalidPackage,
            AppLaunchResult.NotInstalled,
            AppLaunchResult.Disabled,
            AppLaunchResult.NoLaunchIntent,
            AppLaunchResult.ActivityNotFound,
            AppLaunchResult.SecurityBlocked,
            AppLaunchResult.Failed,
        )
        failures.forEach { failure ->
            val repository = FakeRepository()
            val result = LaunchHiddenAppUseCase(FakeRuntime(failure), repository)("target")
            assertEquals(failure, result)
            assertEquals(emptyList<Pair<String, Long>>(), repository.opened)
        }
    }

    @Test
    fun `recent persistence failure does not turn accepted launch into failure`() = runTest {
        val repository = FakeRepository(markFailure = true)
        val useCase = LaunchHiddenAppUseCase(FakeRuntime(AppLaunchResult.Success), repository)

        assertEquals(AppLaunchResult.Success, useCase("target"))
    }

    private class FakeRuntime(
        private val result: AppLaunchResult,
    ) : HiddenAppRuntime {
        override suspend fun resolve(packageName: String) =
            InstalledAppRuntimeInfo(packageName, null, InstalledAppAvailability.Available)

        override suspend fun launch(packageName: String) = result
    }

    private class FakeRepository(
        private val markFailure: Boolean = false,
    ) : HiddenAppRepositoryContract {
        val opened = mutableListOf<Pair<String, Long>>()
        override fun observeHiddenApps(): Flow<List<HiddenApp>> = emptyFlow()
        override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> = emptyFlow()
        override fun observeAddedPackageNames(): Flow<Set<String>> = emptyFlow()
        override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
        override suspend fun addApps(apps: List<InstalledApp>) = 0
        override suspend fun removeApp(packageName: String) = false
        override suspend fun clearRecentHistory() = 0
        override fun refreshAppAvailability() = Unit

        override suspend fun markAppOpened(packageName: String, openedAt: Long): Boolean {
            if (markFailure) error("database unavailable")
            opened += packageName to openedAt
            return true
        }
    }
}
