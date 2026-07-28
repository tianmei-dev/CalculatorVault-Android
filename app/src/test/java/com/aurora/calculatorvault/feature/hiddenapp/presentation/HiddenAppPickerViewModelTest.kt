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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenAppPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search trims query matches Chinese and ignores English case`() = runTest(dispatcher) {
        val viewModel = HiddenAppPickerViewModel(FakeRepository())
        advanceUntilIdle()

        viewModel.updateQuery(" 微信 ")
        assertEquals(listOf("微信"), viewModel.uiState.value.visibleApps.map { it.appName })

        viewModel.updateQuery("NOT")
        assertEquals(listOf("Notion"), viewModel.uiState.value.visibleApps.map { it.appName })

        viewModel.updateQuery("")
        assertEquals(3, viewModel.uiState.value.visibleApps.size)

        viewModel.updateQuery("missing")
        assertTrue(viewModel.uiState.value.visibleApps.isEmpty())
    }

    @Test
    fun `unadded app toggles while already added app cannot be selected`() =
        runTest(dispatcher) {
            val repository = FakeRepository(added = setOf("wechat"))
            val viewModel = HiddenAppPickerViewModel(repository)
            advanceUntilIdle()

            viewModel.toggleSelection("wechat")
            assertTrue(viewModel.uiState.value.selectedPackages.isEmpty())

            viewModel.toggleSelection("notion")
            assertEquals(setOf("notion"), viewModel.uiState.value.selectedPackages)
            assertTrue(viewModel.uiState.value.canSave)

            viewModel.toggleSelection("notion")
            assertTrue(viewModel.uiState.value.selectedPackages.isEmpty())
            assertFalse(viewModel.uiState.value.canSave)
        }

    @Test
    fun `saving multiple selections preserves selection order and emits one completion`() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = HiddenAppPickerViewModel(repository)
            advanceUntilIdle()
            val effect = async { viewModel.effects.first() }
            viewModel.toggleSelection("notion")
            viewModel.toggleSelection("wechat")
            viewModel.toggleSelection("alpha")

            viewModel.saveSelection()
            viewModel.saveSelection()
            advanceUntilIdle()

            assertEquals(listOf("notion", "wechat", "alpha"), repository.saved.map { it.packageName })
            assertEquals(HiddenAppPickerEffect.Completed(3), effect.await())
            assertTrue(viewModel.uiState.value.selectedPackages.isEmpty())
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `save failure retains selection and does not navigate`() = runTest(dispatcher) {
        val repository = FakeRepository(saveFailure = true)
        val viewModel = HiddenAppPickerViewModel(repository)
        advanceUntilIdle()
        viewModel.toggleSelection("wechat")

        viewModel.saveSelection()
        advanceUntilIdle()

        assertEquals(setOf("wechat"), viewModel.uiState.value.selectedPackages)
        assertEquals(HiddenAppError.SaveFailed, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `scan failure exits loading and retry succeeds`() = runTest(dispatcher) {
        val repository = FakeRepository(scanFailures = 1)
        val viewModel = HiddenAppPickerViewModel(repository)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(HiddenAppError.ScanFailed, viewModel.uiState.value.error)

        viewModel.retryScan()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.apps.size)
        assertEquals(null, viewModel.uiState.value.error)
    }

    private class FakeRepository(
        added: Set<String> = emptySet(),
        private val saveFailure: Boolean = false,
        private var scanFailures: Int = 0,
    ) : HiddenAppRepositoryContract {
        private val addedPackages = MutableStateFlow(added)
        val saved = mutableListOf<InstalledApp>()
        private val apps = listOf(
            InstalledApp("wechat", "微信"),
            InstalledApp("notion", "Notion"),
            InstalledApp("alpha", "Alpha"),
        )

        override fun observeHiddenApps(): Flow<List<HiddenApp>> = emptyFlow()
        override fun observeAddedPackageNames(): Flow<Set<String>> = addedPackages

        override suspend fun scanInstalledApps(): List<InstalledApp> {
            if (scanFailures-- > 0) error("scan failed")
            return apps
        }

        override suspend fun addApps(apps: List<InstalledApp>): Int {
            if (saveFailure) error("save failed")
            saved += apps
            addedPackages.value += apps.map(InstalledApp::packageName)
            return apps.size
        }

        override suspend fun removeApp(packageName: String): Boolean = false
    }
}
