package com.aurora.calculatorvault.feature.appmanagement.presentation

import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
class AppManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `room records are exposed as private app count`() = runTest(dispatcher) {
        val viewModel = AppManagementViewModel(
            FakeRepository(
                MutableStateFlow(
                    listOf(hiddenApp("one"), hiddenApp("two"), hiddenApp("three")),
                ),
            ),
            FakeDisguiseRepository(MutableStateFlow(emptyList())),
        )

        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.privateAppCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadFailed)
    }

    @Test
    fun `empty repository exposes zero count`() = runTest(dispatcher) {
        val viewModel = AppManagementViewModel(
            FakeRepository(MutableStateFlow(emptyList())),
            FakeDisguiseRepository(MutableStateFlow(emptyList())),
        )

        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.privateAppCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `repository failure is non blocking state`() = runTest(dispatcher) {
        val failure = flow<List<HiddenApp>> { throw IllegalStateException("read failed") }
        val viewModel = AppManagementViewModel(
            FakeRepository(failure),
            FakeDisguiseRepository(MutableStateFlow(emptyList())),
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, viewModel.uiState.value.privateAppCount)
    }

    @Test
    fun `disguise records are exposed as entry count`() = runTest(dispatcher) {
        val entries = MutableStateFlow(
            listOf(
                disguiseEntry(1),
                disguiseEntry(2),
            ),
        )
        val viewModel = AppManagementViewModel(
            FakeRepository(MutableStateFlow(emptyList())),
            FakeDisguiseRepository(entries),
        )

        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.disguiseEntryCount)
        assertFalse(viewModel.uiState.value.isDisguiseLoading)
    }

    private fun hiddenApp(packageName: String) = HiddenApp(
        packageName = packageName,
        appName = packageName,
        appNameSnapshot = packageName,
        addedAt = 1,
        sortOrder = 0,
        availability = InstalledAppAvailability.Available,
    )

    private fun disguiseEntry(id: Long) = DisguiseEntry(
        id = id,
        packageName = "package.$id",
        targetAppName = "Target $id",
        customName = "Custom $id",
        iconId = DisguiseIconId.Files,
        createdAt = id,
        updatedAt = id,
    )

    private class FakeRepository(
        private val apps: Flow<List<HiddenApp>>,
    ) : HiddenAppRepositoryContract {
        override fun observeHiddenApps(): Flow<List<HiddenApp>> = apps
        override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> = emptyFlow()
        override fun observeAddedPackageNames(): Flow<Set<String>> = emptyFlow()
        override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
        override suspend fun addApps(apps: List<InstalledApp>) = 0
        override suspend fun removeApp(packageName: String) = false
        override suspend fun markAppOpened(packageName: String, openedAt: Long) = false
        override suspend fun clearRecentHistory() = 0
        override fun refreshAppAvailability() = Unit
    }

    private class FakeDisguiseRepository(
        private val entries: Flow<List<DisguiseEntry>>,
    ) : DisguiseEntryRepositoryContract {
        override fun observeEntries() = entries
        override suspend fun scanInstalledApps() = emptyList<InstalledApp>()
        override suspend fun create(
            packageName: String,
            targetAppName: String,
            customName: String,
            iconId: DisguiseIconId,
        ) = 1L
        override suspend fun update(
            id: Long,
            packageName: String,
            targetAppName: String,
            customName: String,
            iconId: DisguiseIconId,
        ) = true
        override suspend fun delete(id: Long) = true
    }
}
