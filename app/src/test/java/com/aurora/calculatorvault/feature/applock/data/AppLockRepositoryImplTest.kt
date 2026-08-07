package com.aurora.calculatorvault.feature.applock.data

import com.aurora.calculatorvault.feature.applock.domain.AppLockPackagePolicyChecker
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.LauncherAppCandidate
import com.aurora.calculatorvault.feature.hiddenapp.domain.LauncherAppSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockRepositoryImplTest {
    @Test
    fun loadLockableApps_filtersRejectedAndNonLaunchableApps() = runTest {
        val repository = repository(
            source = FakeLauncherAppSource(
                listOf(
                    candidate("com.example.allowed", "Allowed"),
                    candidate("com.example.rejected", "Rejected"),
                    candidate("com.example.disabled", "Disabled", isEnabled = false),
                    candidate("com.example.no.launch", "No Launch", hasLaunchIntent = false),
                ),
            ),
            policy = FakePolicy(rejected = setOf("com.example.rejected")),
        )

        val apps = repository.loadLockableApps()

        assertEquals(listOf("com.example.allowed"), apps.map { it.packageName })
    }

    @Test
    fun setLocked_persistsEnabledStateAndLockedPackagesFlowOnlyEmitsEnabled() = runTest {
        val dao = FakeAppLockDao()
        val repository = repository(dao = dao)

        assertEquals(
            AppLockSetResult.Success,
            repository.setLocked("com.example.a", "App A", true),
        )
        assertTrue(repository.isLocked("com.example.a"))
        assertEquals(setOf("com.example.a"), repository.observeLockedPackages().first())

        assertEquals(
            AppLockSetResult.Success,
            repository.setLocked("com.example.a", "App A", false),
        )
        assertFalse(repository.isLocked("com.example.a"))
        assertEquals(emptySet<String>(), repository.observeLockedPackages().first())
    }

    @Test
    fun setLocked_rejectsProtectedPackage() = runTest {
        val repository = repository(policy = FakePolicy(rejected = setOf("com.example.launcher")))

        assertEquals(
            AppLockSetResult.Rejected,
            repository.setLocked("com.example.launcher", "Launcher", true),
        )
    }

    private fun repository(
        dao: FakeAppLockDao = FakeAppLockDao(),
        source: FakeLauncherAppSource = FakeLauncherAppSource(listOf(candidate("com.example.a", "App A"))),
        policy: AppLockPackagePolicyChecker = FakePolicy(),
    ) = AppLockRepositoryImpl(
        dao = dao,
        launcherAppSource = source,
        packagePolicy = policy,
        currentTimeMillis = { 100L },
    )

    private class FakePolicy(
        private val rejected: Set<String> = emptySet(),
    ) : AppLockPackagePolicyChecker {
        override fun canBeLocked(packageName: String): Boolean = packageName !in rejected
    }

    private class FakeLauncherAppSource(
        private val apps: List<LauncherAppCandidate>,
    ) : LauncherAppSource {
        override fun queryLauncherApps(): List<LauncherAppCandidate> = apps
        override fun resolve(packageName: String): LauncherAppCandidate? =
            apps.firstOrNull { it.packageName == packageName }
    }

    private class FakeAppLockDao : AppLockDao {
        private val entries = MutableStateFlow<List<AppLockEntryEntity>>(emptyList())

        override fun observeAllEntries() = entries
        override fun observeEnabledEntries() = MutableStateFlow(entries.value.filter { it.enabled })
        override fun observeEnabledPackages() =
            MutableStateFlow(entries.value.filter { it.enabled }.map { it.packageName })

        override suspend fun getEntry(packageName: String): AppLockEntryEntity? =
            entries.value.firstOrNull { it.packageName == packageName }

        override suspend fun upsert(entry: AppLockEntryEntity) {
            entries.value = entries.value.filterNot { it.packageName == entry.packageName } + entry
        }

        override suspend fun updateEnabled(
            packageName: String,
            enabled: Boolean,
            appNameSnapshot: String,
            updatedAt: Long,
        ): Int {
            var updated = 0
            entries.value = entries.value.map {
                if (it.packageName == packageName) {
                    updated += 1
                    it.copy(
                        enabled = enabled,
                        appNameSnapshot = appNameSnapshot,
                        updatedAt = updatedAt,
                    )
                } else {
                    it
                }
            }
            return updated
        }

        override suspend fun delete(packageName: String): Int {
            val before = entries.value.size
            entries.value = entries.value.filterNot { it.packageName == packageName }
            return before - entries.value.size
        }

        override suspend fun deleteAll(): Int {
            val before = entries.value.size
            entries.value = emptyList()
            return before
        }
    }
}

private fun candidate(
    packageName: String,
    appName: String,
    isEnabled: Boolean = true,
    hasLaunchIntent: Boolean = true,
) = LauncherAppCandidate(
    packageName = packageName,
    appName = appName,
    isSystemApp = false,
    isUpdatedSystemApp = false,
    isEnabled = isEnabled,
    isInstantApp = false,
    hasLaunchIntent = hasLaunchIntent,
)
