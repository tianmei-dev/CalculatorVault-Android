package com.aurora.calculatorvault.feature.hiddenapp.presentation

import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
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
        isInstalled = true,
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
    fun `cancel removal keeps record`() = runTest(dispatcher) {
        val repository = FakeRepository(app)
        val viewModel = HiddenAppViewModel(repository)
        advanceUntilIdle()

        viewModel.requestRemoval(app)
        viewModel.cancelRemoval()

        assertNull(viewModel.uiState.value.pendingRemoval)
        assertEquals(listOf(app), viewModel.uiState.value.apps)
    }

    @Test
    fun `confirmed removal updates list and emits success`() = runTest(dispatcher) {
        val repository = FakeRepository(app)
        val viewModel = HiddenAppViewModel(repository)
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.requestRemoval(app)
        viewModel.confirmRemoval()
        advanceUntilIdle()

        assertEquals(HiddenAppEffect.Removed, effect.await())
        assertTrue(viewModel.uiState.value.apps.isEmpty())
        assertNull(viewModel.uiState.value.pendingRemoval)
    }

    @Test
    fun `remove failure retains record and pending confirmation`() = runTest(dispatcher) {
        val repository = FakeRepository(app, removeSucceeds = false)
        val viewModel = HiddenAppViewModel(repository)
        advanceUntilIdle()

        viewModel.requestRemoval(app)
        viewModel.confirmRemoval()
        advanceUntilIdle()

        assertEquals(HiddenAppError.RemoveFailed, viewModel.uiState.value.error)
        assertEquals(app, viewModel.uiState.value.pendingRemoval)
        assertEquals(listOf(app), viewModel.uiState.value.apps)
    }

    private class FakeRepository(
        initial: HiddenApp,
        private val removeSucceeds: Boolean = true,
    ) : HiddenAppRepositoryContract {
        private val apps = MutableStateFlow(listOf(initial))

        override fun observeHiddenApps(): Flow<List<HiddenApp>> = apps
        override fun observeAddedPackageNames(): Flow<Set<String>> = emptyFlow()
        override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
        override suspend fun addApps(apps: List<InstalledApp>): Int = 0

        override suspend fun removeApp(packageName: String): Boolean {
            if (!removeSucceeds) return false
            apps.value = apps.value.filterNot { it.packageName == packageName }
            return true
        }
    }
}
