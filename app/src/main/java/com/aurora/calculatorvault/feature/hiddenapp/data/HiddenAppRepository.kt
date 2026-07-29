package com.aurora.calculatorvault.feature.hiddenapp.data

import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

interface HiddenAppRepositoryContract {
    fun observeHiddenApps(): Flow<List<HiddenApp>>
    fun observeRecentApps(limit: Int): Flow<List<HiddenApp>>
    fun observeAddedPackageNames(): Flow<Set<String>>
    suspend fun scanInstalledApps(): List<InstalledApp>
    suspend fun addApps(apps: List<InstalledApp>): Int
    suspend fun removeApp(packageName: String): Boolean
    suspend fun removeApps(packageNames: List<String>): Int =
        packageNames.distinct().count { removeApp(it) }
    suspend fun updateManualOrder(packageNames: List<String>): Boolean = false
    suspend fun markAppOpened(packageName: String, openedAt: Long): Boolean
    suspend fun clearRecentHistory(): Int
    suspend fun openAppSettings(packageName: String): Boolean = false
    fun refreshAppAvailability()
}

class HiddenAppRepository(
    private val scanner: InstalledAppScanner,
    private val runtime: HiddenAppRuntime,
    private val store: HiddenAppStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : HiddenAppRepositoryContract {
    private val refreshVersion = MutableStateFlow(0L)

    override fun observeHiddenApps(): Flow<List<HiddenApp>> =
        store.observeAll().combine(refreshVersion) { entities, _ -> mapEntities(entities) }

    override fun observeRecentApps(limit: Int): Flow<List<HiddenApp>> =
        store.observeRecent(limit).combine(refreshVersion) { entities, _ -> mapEntities(entities) }

    override fun observeAddedPackageNames(): Flow<Set<String>> = store.observePackageNames()

    override suspend fun scanInstalledApps(): List<InstalledApp> = scanner.scan()

    override suspend fun addApps(apps: List<InstalledApp>): Int {
        val launchable = apps
            .distinctBy(InstalledApp::packageName)
            .mapNotNull { selected -> scanner.resolve(selected.packageName) }
        return store.addUnique(launchable, currentTimeMillis())
    }

    override suspend fun removeApp(packageName: String): Boolean = store.remove(packageName)

    override suspend fun removeApps(packageNames: List<String>): Int =
        store.removeMany(packageNames)

    override suspend fun updateManualOrder(packageNames: List<String>): Boolean =
        store.updateManualOrder(packageNames)

    override suspend fun markAppOpened(packageName: String, openedAt: Long): Boolean =
        store.markOpened(packageName, openedAt)

    override suspend fun clearRecentHistory(): Int = store.clearRecentHistory()

    override suspend fun openAppSettings(packageName: String): Boolean =
        runtime.openSettings(packageName)

    override fun refreshAppAvailability() {
        refreshVersion.value += 1
    }

    private suspend fun mapEntities(entities: List<HiddenAppEntity>): List<HiddenApp> =
        entities.map { entity ->
            val current = runtime.resolve(entity.packageName)
            HiddenApp(
                packageName = entity.packageName,
                appName = current.appName ?: entity.appNameSnapshot,
                appNameSnapshot = entity.appNameSnapshot,
                addedAt = entity.addedAt,
                sortOrder = entity.sortOrder,
                availability = current.availability,
                lastOpenedAt = entity.lastOpenedAt,
                openCount = entity.openCount,
            )
        }
}
