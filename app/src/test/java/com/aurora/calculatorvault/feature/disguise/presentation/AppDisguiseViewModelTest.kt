package com.aurora.calculatorvault.feature.disguise.presentation

import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseSortMode
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestError
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.disguise.shortcut.PinShortcutRequest
import com.aurora.calculatorvault.feature.disguise.shortcut.PinShortcutRequestResult
import com.aurora.calculatorvault.feature.disguise.shortcut.PinnedShortcutCreator
import com.aurora.calculatorvault.feature.disguise.shortcut.RequestPinShortcutUseCase
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class AppDisguiseViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create wizard reuses scanned apps and saves configuration`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = AppDisguiseViewModel(repository)
        viewModel.startCreate()
        advanceUntilIdle()

        assertEquals(listOf("Target"), viewModel.uiState.value.installedApps.map { it.appName })
        viewModel.selectApp(repository.apps.single())
        viewModel.updateCustomName("Work")
        viewModel.continueFromName()
        viewModel.selectIcon(DisguiseIconId.Calendar)
        viewModel.continueToPreview()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(AppDisguisePage.Saved, viewModel.uiState.value.page)
        assertEquals("Work", repository.entries.value.single().customName)
        assertEquals(DisguiseIconId.Calendar, repository.entries.value.single().iconId)
    }

    @Test
    fun `search matches custom and target names and sort modes work`() = runTest(dispatcher) {
        val repository = FakeRepository(
            initial = listOf(
                entry(1, "便签", "Target A", created = 1, updated = 30),
                entry(2, "Browser", "目标浏览器", created = 20, updated = 10),
            ),
        )
        val viewModel = AppDisguiseViewModel(repository)
        advanceUntilIdle()

        viewModel.updateQuery("目标浏览器")
        assertEquals(listOf("Browser"), viewModel.uiState.value.visibleEntries.map { it.customName })
        viewModel.updateQuery("")
        viewModel.setSortMode(DisguiseSortMode.CreatedNewest)
        assertEquals(listOf(2L, 1L), viewModel.uiState.value.visibleEntries.map { it.id })
        viewModel.setSortMode(DisguiseSortMode.UpdatedNewest)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.visibleEntries.map { it.id })
    }

    @Test
    fun `edit and delete update existing entry without touching target app`() = runTest(dispatcher) {
        val repository = FakeRepository(initial = listOf(entry(7, "Old", "Target", 1, 1)))
        val viewModel = AppDisguiseViewModel(repository)
        advanceUntilIdle()

        viewModel.edit(repository.entries.value.single())
        viewModel.updateCustomName("New")
        viewModel.continueFromName()
        viewModel.selectIcon(DisguiseIconId.Tools)
        viewModel.continueToPreview()
        viewModel.save()
        advanceUntilIdle()

        assertEquals("New", repository.entries.value.single().customName)
        assertEquals("target.package", repository.entries.value.single().packageName)

        viewModel.requestDelete(repository.entries.value.single())
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `empty name cannot advance and input is limited to twenty characters`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = AppDisguiseViewModel(repository)
        viewModel.startCreate()
        advanceUntilIdle()
        viewModel.selectApp(repository.apps.single())
        viewModel.updateCustomName("")
        viewModel.continueFromName()
        assertEquals(AppDisguisePage.SetName, viewModel.uiState.value.page)
        assertFalse(viewModel.uiState.value.canContinueName)

        viewModel.updateCustomName("1234567890123456789012345")
        assertEquals(20, viewModel.uiState.value.customName.length)
    }

    @Test
    fun `submitted shortcut requires confirmation before another request`() = runTest(dispatcher) {
        val existing = entry(9, "Work", "Target", 1, 1).copy(
            shortcutId = "cv_disguise_existing",
            shortcutRequestState = ShortcutRequestState.RequestSubmitted,
        )
        val repository = FakeRepository(initial = listOf(existing))
        val creator = FakePinnedCreator()
        val viewModel = AppDisguiseViewModel(
            repository,
            RequestPinShortcutUseCase(repository, creator),
        )
        advanceUntilIdle()

        viewModel.requestShortcut(existing)
        assertEquals(existing, viewModel.uiState.value.pendingDuplicateRequest)
        assertEquals(0, creator.requestCount)

        viewModel.confirmDuplicateRequest()
        advanceUntilIdle()
        assertEquals(1, creator.requestCount)
        assertTrue(viewModel.uiState.value.showRequestSubmittedDialog)
    }

    private fun entry(
        id: Long,
        custom: String,
        target: String,
        created: Long,
        updated: Long,
    ) = DisguiseEntry(
        id = id,
        packageName = "target.package",
        targetAppName = target,
        customName = custom,
        iconId = DisguiseIconId.Files,
        createdAt = created,
        updatedAt = updated,
    )

    private class FakeRepository(
        initial: List<DisguiseEntry> = emptyList(),
    ) : DisguiseEntryRepositoryContract {
        val entries = MutableStateFlow(initial)
        val apps = listOf(InstalledApp("target.package", "Target"))
        private var nextId = 100L

        override fun observeEntries() = entries
        override suspend fun scanInstalledApps() = apps

        override suspend fun create(
            packageName: String,
            targetAppName: String,
            customName: String,
            iconId: DisguiseIconId,
        ): Long {
            val id = nextId++
            entries.value += DisguiseEntry(
                id, packageName, targetAppName, customName, iconId, 10, 10,
            )
            return id
        }

        override suspend fun update(
            id: Long,
            packageName: String,
            targetAppName: String,
            customName: String,
            iconId: DisguiseIconId,
        ): Boolean {
            val old = entries.value.firstOrNull { it.id == id } ?: return false
            entries.value = entries.value.map {
                if (it.id == id) old.copy(
                    packageName = packageName,
                    targetAppName = targetAppName,
                    customName = customName,
                    iconId = iconId,
                    updatedAt = old.updatedAt + 1,
                ) else it
            }
            return true
        }

        override suspend fun delete(id: Long): Boolean {
            val previous = entries.value
            entries.value = previous.filterNot { it.id == id }
            return previous.size != entries.value.size
        }

        override suspend fun findById(id: Long) = entries.value.firstOrNull { it.id == id }

        override suspend fun ensureShortcutId(id: Long): String? {
            val existing = entries.value.firstOrNull { it.id == id } ?: return null
            val shortcutId = existing.shortcutId ?: "cv_disguise_generated"
            entries.value = entries.value.map {
                if (it.id == id) it.copy(shortcutId = shortcutId) else it
            }
            return shortcutId
        }

        override suspend fun updateShortcutRequest(
            id: Long,
            state: ShortcutRequestState,
            requestedAt: Long?,
            error: ShortcutRequestError?,
        ): Boolean {
            if (entries.value.none { it.id == id }) return false
            entries.value = entries.value.map {
                if (it.id == id) it.copy(
                    shortcutRequestState = state,
                    shortcutRequestedAt = requestedAt,
                    shortcutLastError = error,
                ) else it
            }
            return true
        }
    }

    private class FakePinnedCreator : PinnedShortcutCreator {
        var requestCount = 0
        override fun isSupported() = true
        override suspend fun requestPinShortcut(
            request: PinShortcutRequest,
        ): PinShortcutRequestResult {
            requestCount += 1
            return PinShortcutRequestResult.RequestSubmitted
        }
    }
}
