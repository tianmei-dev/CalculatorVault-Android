package com.aurora.calculatorvault.feature.hiddenapp.presentation

import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppPreferenceState
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppPreferences
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppLayoutMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppSortMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppRuntimeInfo
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenAppExperienceTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search matches name package and ignores case`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateQuery("youtube")
        assertEquals(listOf("YouTube"), viewModel.uiState.value.visibleApps.map(HiddenApp::appName))
        viewModel.updateQuery("reader.pkg")
        assertEquals(listOf("微信读书"), viewModel.uiState.value.visibleApps.map(HiddenApp::appName))
        viewModel.clearQuery()
        assertEquals(3, viewModel.uiState.value.visibleApps.size)
    }

    @Test
    fun `layout and all sort modes update without changing manual order`() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val viewModel = viewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.toggleLayout()
        advanceUntilIdle()
        assertEquals(HiddenAppLayoutMode.List, viewModel.uiState.value.layoutMode)
        assertEquals(HiddenAppLayoutMode.List, preferences.value.value.layoutMode)

        viewModel.changeSortMode(HiddenAppSortMode.AddedNewest)
        assertEquals(listOf("微信读书", "YouTube", "微信"), names(viewModel))
        viewModel.changeSortMode(HiddenAppSortMode.AddedOldest)
        assertEquals(listOf("微信", "YouTube", "微信读书"), names(viewModel))
        viewModel.changeSortMode(HiddenAppSortMode.NameAscending)
        assertEquals(3, names(viewModel).size)
        viewModel.changeSortMode(HiddenAppSortMode.NameDescending)
        assertEquals(3, names(viewModel).size)
        viewModel.changeSortMode(HiddenAppSortMode.Manual)
        assertEquals(listOf("微信", "YouTube", "微信读书"), names(viewModel))
    }

    @Test
    fun `manual move saves one complete order and cancel does not write`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.enterManualSort()
        viewModel.moveManualApp(2, 0)
        viewModel.saveManualSort()
        advanceUntilIdle()
        assertEquals(listOf("reader.pkg", "wechat.pkg", "youtube.pkg"), repository.savedOrder)

        repository.savedOrder = emptyList()
        viewModel.enterManualSort()
        viewModel.moveManualApp(0, 2)
        viewModel.requestCancelManualSort()
        viewModel.cancelManualSort()
        assertTrue(repository.savedOrder.isEmpty())
        assertFalse(viewModel.uiState.value.isManualSortMode)
    }

    @Test
    fun `batch select all uses visible results and failed removal keeps selection`() =
        runTest(dispatcher) {
            val repository = FakeRepository().apply { batchRemoveFails = true }
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.enterBatchMode()
            viewModel.updateQuery("微信")
            viewModel.toggleSelectAllVisible()
            assertEquals(setOf("wechat.pkg", "reader.pkg"), viewModel.uiState.value.selectedPackages)
            viewModel.requestBatchRemoval()
            viewModel.confirmBatchRemoval()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isBatchMode)
            assertEquals(2, viewModel.uiState.value.selectedPackages.size)
        }

    @Test
    fun `select invalid includes only missing and no launcher`() = runTest(dispatcher) {
        val repository = FakeRepository(
            apps = listOf(
                app("missing", "Missing", 1, 0, InstalledAppAvailability.NotInstalled),
                app("nol", "No launcher", 2, 1, InstalledAppAvailability.NoLauncher),
                app("disabled", "Disabled", 3, 2, InstalledAppAvailability.Disabled),
                app("ok", "Available", 4, 3),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.enterBatchMode()
        viewModel.selectInvalidApps()
        assertEquals(setOf("missing", "nol"), viewModel.uiState.value.selectedPackages)
    }

    private fun names(viewModel: HiddenAppViewModel) =
        viewModel.uiState.value.visibleApps.map(HiddenApp::appName)

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        preferences: FakePreferences = FakePreferences(),
    ) = HiddenAppViewModel(
        repository,
        LaunchHiddenAppUseCase(FakeRuntime(), repository),
        preferences,
    )

    private class FakePreferences : HiddenAppPreferences {
        val value = MutableStateFlow(HiddenAppPreferenceState())
        override val state: Flow<HiddenAppPreferenceState> = value
        override suspend fun setLayoutMode(mode: HiddenAppLayoutMode) {
            value.value = value.value.copy(layoutMode = mode)
        }
        override suspend fun setSortMode(mode: HiddenAppSortMode) {
            value.value = value.value.copy(sortMode = mode)
        }
        override suspend fun dismissUsageNotice() {
            value.value = value.value.copy(usageNoticeDismissed = true)
        }
        override suspend fun markBatchTipSeen() = Unit
        override suspend fun markSortTipSeen() = Unit
    }

    private class FakeRuntime : HiddenAppRuntime {
        override suspend fun resolve(packageName: String) =
            InstalledAppRuntimeInfo(packageName, null, InstalledAppAvailability.Available)
        override suspend fun launch(packageName: String) = AppLaunchResult.Success
    }

    private class FakeRepository(
        apps: List<HiddenApp> = listOf(
            app("wechat.pkg", "微信", 1, 0),
            app("youtube.pkg", "YouTube", 2, 1),
            app("reader.pkg", "微信读书", 3, 2),
        ),
    ) : HiddenAppRepositoryContract {
        private val appFlow = MutableStateFlow(apps)
        var savedOrder: List<String> = emptyList()
        var batchRemoveFails = false
        override fun observeHiddenApps(): Flow<List<HiddenApp>> = appFlow
        override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> =
            MutableStateFlow(emptyList())
        override fun observeAddedPackageNames(): Flow<Set<String>> = emptyFlow()
        override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
        override suspend fun addApps(apps: List<InstalledApp>): Int = 0
        override suspend fun removeApp(packageName: String): Boolean = true
        override suspend fun removeApps(packageNames: List<String>): Int {
            if (batchRemoveFails) error("failure")
            appFlow.value = appFlow.value.filterNot { it.packageName in packageNames }
            return packageNames.size
        }
        override suspend fun updateManualOrder(packageNames: List<String>): Boolean {
            savedOrder = packageNames
            appFlow.value = packageNames.mapIndexedNotNull { index, packageName ->
                appFlow.value.firstOrNull { it.packageName == packageName }?.copy(sortOrder = index)
            }
            return true
        }
        override suspend fun markAppOpened(packageName: String, openedAt: Long) = true
        override suspend fun clearRecentHistory() = 0
        override fun refreshAppAvailability() = Unit
    }

    companion object {
        private fun app(
            packageName: String,
            name: String,
            addedAt: Long,
            sortOrder: Int,
            availability: InstalledAppAvailability = InstalledAppAvailability.Available,
        ) = HiddenApp(
            packageName,
            name,
            name,
            addedAt,
            sortOrder,
            availability,
        )
    }
}
