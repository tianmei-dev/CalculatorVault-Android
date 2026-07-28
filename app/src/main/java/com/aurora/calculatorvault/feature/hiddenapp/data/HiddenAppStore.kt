package com.aurora.calculatorvault.feature.hiddenapp.data

import androidx.room.withTransaction
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HiddenAppStore {
    fun observeAll(): Flow<List<HiddenAppEntity>>
    fun observePackageNames(): Flow<Set<String>>
    suspend fun addUnique(apps: List<InstalledApp>, addedAt: Long): Int
    suspend fun remove(packageName: String): Boolean
}

class RoomHiddenAppStore(
    private val database: CalculatorVaultDatabase,
) : HiddenAppStore {
    private val dao = database.hiddenAppDao()

    override fun observeAll(): Flow<List<HiddenAppEntity>> = dao.observeAll()

    override fun observePackageNames(): Flow<Set<String>> =
        dao.observeAllPackageNames().map(List<String>::toSet)

    override suspend fun addUnique(
        apps: List<InstalledApp>,
        addedAt: Long,
    ): Int = database.withTransaction {
        val unique = apps.distinctBy(InstalledApp::packageName)
        if (unique.isEmpty()) return@withTransaction 0
        val existing = dao.findExistingPackageNames(unique.map(InstalledApp::packageName)).toSet()
        var sortOrder = (dao.maxSortOrder() ?: -1) + 1
        val entities = unique
            .filterNot { it.packageName in existing }
            .map { app ->
                HiddenAppEntity(
                    packageName = app.packageName,
                    appNameSnapshot = app.appName,
                    addedAt = addedAt,
                    sortOrder = sortOrder++,
                )
            }
        dao.insertAll(entities).count { id -> id != INSERT_IGNORED }
    }

    override suspend fun remove(packageName: String): Boolean =
        dao.deleteByPackageName(packageName) > 0

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
