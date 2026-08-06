package com.aurora.calculatorvault.feature.disguise.shortcut

import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryRepositoryContract
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestError
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppRepositoryContract
import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppRuntimeInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal const val TEST_SHORTCUT_ID = "cv_disguise_123e4567-e89b-42d3-a456-426614174000"
internal const val TEST_PACKAGE = "com.example.target"

internal fun testEntry(shortcutId: String = TEST_SHORTCUT_ID) = DisguiseEntry(
    id = 1,
    packageName = TEST_PACKAGE,
    targetAppName = "Target",
    customName = "Notes",
    iconId = DisguiseIconId.Files,
    createdAt = 1,
    updatedAt = 1,
    shortcutId = shortcutId,
)

internal class FakeDisguiseRepository(
    var entry: DisguiseEntry? = testEntry(),
) : DisguiseEntryRepositoryContract {
    override fun observeEntries(): Flow<List<DisguiseEntry>> = flowOf(listOfNotNull(entry))
    override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
    override suspend fun create(packageName: String, targetAppName: String, customName: String, iconId: DisguiseIconId) = 1L
    override suspend fun update(id: Long, packageName: String, targetAppName: String, customName: String, iconId: DisguiseIconId) = true
    override suspend fun delete(id: Long) = true
    override suspend fun findByShortcutId(shortcutId: String): DisguiseEntry? =
        entry?.takeIf { it.shortcutId == shortcutId }
    override suspend fun updateShortcutRequest(id: Long, state: ShortcutRequestState, requestedAt: Long?, error: ShortcutRequestError?) = true
}

internal class FakeRuntime(
    var availability: InstalledAppAvailability = InstalledAppAvailability.Available,
    var launchResult: AppLaunchResult = AppLaunchResult.Success,
) : HiddenAppRuntime {
    var launchCalls = 0
    override suspend fun resolve(packageName: String) =
        InstalledAppRuntimeInfo(packageName, "Target", availability)
    override suspend fun launch(packageName: String): AppLaunchResult {
        launchCalls += 1
        return launchResult
    }
}

internal class FakeHiddenRepository : HiddenAppRepositoryContract {
    var markOpenedCalls = 0
    override fun observeHiddenApps(): Flow<List<HiddenApp>> = flowOf(emptyList())
    override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> = flowOf(emptyList())
    override fun observeAddedPackageNames(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun scanInstalledApps(): List<InstalledApp> = emptyList()
    override suspend fun addApps(apps: List<InstalledApp>) = 0
    override suspend fun removeApp(packageName: String) = false
    override suspend fun markAppOpened(packageName: String, openedAt: Long): Boolean {
        markOpenedCalls += 1
        return false
    }
    override suspend fun clearRecentHistory() = 0
    override fun refreshAppAvailability() = Unit
}
