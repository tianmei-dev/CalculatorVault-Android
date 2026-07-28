package com.aurora.calculatorvault.feature.hiddenapp.data

import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HiddenAppRepositoryContract {
    fun observeHiddenApps(): Flow<List<HiddenApp>>
    fun observeAddedPackageNames(): Flow<Set<String>>
    suspend fun scanInstalledApps(): List<InstalledApp>
    suspend fun addApps(apps: List<InstalledApp>): Int
    suspend fun removeApp(packageName: String): Boolean
}

class HiddenAppRepository(
    private val scanner: InstalledAppScanner,
    private val store: HiddenAppStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : HiddenAppRepositoryContract {

    override fun observeHiddenApps(): Flow<List<HiddenApp>> =
        store.observeAll().map { entities ->
            entities.map { entity ->
                val current = scanner.resolve(entity.packageName)
                HiddenApp(
                    packageName = entity.packageName,
                    appName = current?.appName ?: entity.appNameSnapshot,
                    appNameSnapshot = entity.appNameSnapshot,
                    addedAt = entity.addedAt,
                    sortOrder = entity.sortOrder,
                    isInstalled = current != null,
                )
            }
        }

    override fun observeAddedPackageNames(): Flow<Set<String>> = store.observePackageNames()

    override suspend fun scanInstalledApps(): List<InstalledApp> = scanner.scan()

    override suspend fun addApps(apps: List<InstalledApp>): Int {
        val launchable = apps
            .distinctBy(InstalledApp::packageName)
            .mapNotNull { selected -> scanner.resolve(selected.packageName) }
        return store.addUnique(launchable, currentTimeMillis())
    }

    override suspend fun removeApp(packageName: String): Boolean = store.remove(packageName)
}
