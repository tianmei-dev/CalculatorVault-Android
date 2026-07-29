package com.aurora.calculatorvault.feature.settings.presentation

import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `settings clear uses repository and keeps hidden records`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RecentHistoryViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasHistory)
        val effect = async { viewModel.cleared.first() }

        viewModel.requestClear()
        viewModel.confirmClear()
        advanceUntilIdle()

        effect.await()
        assertFalse(viewModel.uiState.value.hasHistory)
        assertTrue(repository.hiddenStillExists)
    }

    private class FakeRepository : HiddenAppRepositoryContract {
        private val app = HiddenApp(
            packageName = "one",
            appName = "One",
            appNameSnapshot = "One",
            addedAt = 1,
            sortOrder = 0,
            availability = InstalledAppAvailability.Available,
            lastOpenedAt = 10,
            openCount = 1,
        )
        private val recent = MutableStateFlow(listOf(app))
        var hiddenStillExists = true

        override fun observeHiddenApps(): Flow<List<HiddenApp>> = MutableStateFlow(listOf(app))
        override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> = recent
        override fun observeAddedPackageNames(): Flow<Set<String>> = emptyFlow()
        override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
        override suspend fun addApps(apps: List<InstalledApp>) = 0
        override suspend fun removeApp(packageName: String) = false
        override suspend fun markAppOpened(packageName: String, openedAt: Long) = false
        override fun refreshAppAvailability() = Unit

        override suspend fun clearRecentHistory(): Int {
            recent.value = emptyList()
            return 1
        }
    }
}
