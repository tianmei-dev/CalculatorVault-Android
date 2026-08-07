package com.aurora.calculatorvault.feature.applock.domain

import kotlinx.coroutines.flow.Flow

interface AppLockRepository {
    fun observeEntries(): Flow<List<AppLockEntry>>
    fun observeLockedPackages(): Flow<Set<String>>
    suspend fun loadLockableApps(): List<LockableApp>
    suspend fun setLocked(packageName: String, appName: String, locked: Boolean): AppLockSetResult
    suspend fun isLocked(packageName: String): Boolean
}
