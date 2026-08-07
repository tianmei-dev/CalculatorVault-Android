package com.aurora.calculatorvault.feature.applock.data

import com.aurora.calculatorvault.feature.applock.domain.AppLockEntry
import com.aurora.calculatorvault.feature.applock.domain.AppLockPackagePolicyChecker
import com.aurora.calculatorvault.feature.applock.domain.AppLockRepository
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.applock.domain.LockableApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.LauncherAppCandidate
import com.aurora.calculatorvault.feature.hiddenapp.domain.LauncherAppSource
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppLockRepositoryImpl(
    private val dao: AppLockDao,
    private val launcherAppSource: LauncherAppSource,
    private val packagePolicy: AppLockPackagePolicyChecker,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val collator: Collator = Collator.getInstance(Locale.getDefault()),
) : AppLockRepository {
    override fun observeEntries(): Flow<List<AppLockEntry>> =
        dao.observeAllEntries().map { entries -> entries.map { it.toDomain() } }

    override fun observeLockedPackages(): Flow<Set<String>> =
        dao.observeEnabledPackages().map { packages ->
            packages.filter(packagePolicy::canBeLocked).toSet()
        }

    override suspend fun loadLockableApps(): List<LockableApp> = withContext(ioDispatcher) {
        val locked = observeLockedPackages().first()
        launcherAppSource.queryLauncherApps()
            .asSequence()
            .filter(::isEligible)
            .distinctBy(LauncherAppCandidate::packageName)
            .map {
                LockableApp(
                    packageName = it.packageName,
                    appName = it.appName,
                    locked = it.packageName in locked,
                )
            }
            .sortedWith { left, right -> collator.compare(left.appName, right.appName) }
            .toList()
    }

    override suspend fun setLocked(
        packageName: String,
        appName: String,
        locked: Boolean,
    ): AppLockSetResult = withContext(ioDispatcher) {
        if (!packagePolicy.canBeLocked(packageName)) return@withContext AppLockSetResult.Rejected
        try {
            val now = currentTimeMillis()
            val existing = dao.getEntry(packageName)
            dao.upsert(
                AppLockEntryEntity(
                    packageName = packageName,
                    appNameSnapshot = appName.ifBlank { existing?.appNameSnapshot.orEmpty() },
                    enabled = locked,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            AppLockSetResult.Success
        } catch (_: Exception) {
            AppLockSetResult.Failed
        }
    }

    override suspend fun isLocked(packageName: String): Boolean = withContext(ioDispatcher) {
        dao.getEntry(packageName)?.enabled == true && packagePolicy.canBeLocked(packageName)
    }

    private fun isEligible(candidate: LauncherAppCandidate): Boolean =
        candidate.packageName.isNotBlank() &&
            candidate.appName.isNotBlank() &&
            candidate.isEnabled &&
            !candidate.isInstantApp &&
            candidate.hasLaunchIntent &&
            packagePolicy.canBeLocked(candidate.packageName)

    private fun AppLockEntryEntity.toDomain(): AppLockEntry =
        AppLockEntry(
            packageName = packageName,
            appNameSnapshot = appNameSnapshot,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
