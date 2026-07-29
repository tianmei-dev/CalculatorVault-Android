package com.aurora.calculatorvault.feature.hiddenapp.presentation

import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppRuntimeInfo
import com.aurora.calculatorvault.feature.hiddenapp.domain.LaunchHiddenAppUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenAppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val app = HiddenApp(
        packageName = "wechat",
        appName = "微信",
        appNameSnapshot = "微信",
        addedAt = 1,
        sortOrder = 0,
        availability = InstalledAppAvailability.Available,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful launch records once and duplicate tap is ignored`() = runTest(dispatcher) {
        val repository = FakeRepository(app)
        val gate = CompletableDeferred<Unit>()
        val runtime = FakeRuntime(AppLaunchResult.Success, gate)
        val viewModel = viewModel(repository, runtime)
        advanceUntilIdle()

        viewModel.launchApp(app)
        runCurrent()
        viewModel.launchApp(app)
        assertEquals(1, runtime.calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.markOpenedCalls)
        assertNull(viewModel.uiState.value.launchingPackageName)
    }

    @Test
    fun `failed launch does not record and exposes structured error`() = runTest(dispatcher) {
        val repository = FakeRepository(app)
        val viewModel = viewModel(repository, FakeRuntime(AppLaunchResult.NotInstalled))
        advanceUntilIdle()

        viewModel.launchApp(app)
        advanceUntilIdle()

        assertEquals(0, repository.markOpenedCalls)
        assertEquals(HiddenAppError.NotInstalled, viewModel.uiState.value.error)
        assertEquals(app, viewModel.uiState.value.launchErrorApp)
    }

    @Test
    fun `recent list is limited to five and clear preserves all apps`() = runTest(dispatcher) {
        val six = (1..6).map {
            app.copy(packageName = "app$it", appName = "App $it", lastOpenedAt = it.toLong())
        }
        val repository = FakeRepository(*six.toTypedArray())
        repository.recent.value = six.reversed()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.recentApps.size)
        val effect = async { viewModel.effects.first() }
        viewModel.requestClearRecent()
        viewModel.confirmClearRecent()
        advanceUntilIdle()

        assertEquals(HiddenAppEffect.RecentCleared, effect.await())
        assertTrue(viewModel.uiState.value.recentApps.isEmpty())
        assertEquals(6, viewModel.uiState.value.apps.size)
    }

    @Test
    fun `confirmed removal updates all and recent streams`() = runTest(dispatcher) {
        val repository = FakeRepository(app)
        repository.recent.value = listOf(app.copy(lastOpenedAt = 1))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.requestRemoval(app)
        viewModel.confirmRemoval()
        advanceUntilIdle()

        assertEquals(HiddenAppEffect.Removed, effect.await())
        assertTrue(viewModel.uiState.value.apps.isEmpty())
        assertTrue(viewModel.uiState.value.recentApps.isEmpty())
    }

    private fun viewModel(
        repository: FakeRepository,
        runtime: FakeRuntime = FakeRuntime(AppLaunchResult.Success),
    ) = HiddenAppViewModel(
        repository,
        LaunchHiddenAppUseCase(runtime, repository) { 99L },
    )

    private class FakeRuntime(
        private val result: AppLaunchResult,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : HiddenAppRuntime {
        var calls = 0
        override suspend fun resolve(packageName: String) =
            InstalledAppRuntimeInfo(packageName, null, InstalledAppAvailability.Available)

        override suspend fun launch(packageName: String): AppLaunchResult {
            calls++
            gate?.await()
            return result
        }
    }

    private class FakeRepository(
        vararg initial: HiddenApp,
    ) : HiddenAppRepositoryContract {
        private val apps = MutableStateFlow(initial.toList())
        val recent = MutableStateFlow<List<HiddenApp>>(emptyList())
        var markOpenedCalls = 0

        override fun observeHiddenApps(): Flow<List<HiddenApp>> = apps
        override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> = recent
        override fun observeAddedPackageNames(): Flow<Set<String>> = emptyFlow()
        override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
        override suspend fun addApps(apps: List<InstalledApp>): Int = 0

        override suspend fun removeApp(packageName: String): Boolean {
            apps.value = apps.value.filterNot { it.packageName == packageName }
            recent.value = recent.value.filterNot { it.packageName == packageName }
            return true
        }

        override suspend fun markAppOpened(packageName: String, openedAt: Long): Boolean {
            markOpenedCalls++
            return true
        }

        override suspend fun clearRecentHistory(): Int {
            val count = recent.value.size
            recent.value = emptyList()
            return count
        }

        override fun refreshAppAvailability() = Unit
    }
}
