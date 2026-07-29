package com.aurora.calculatorvault.feature.hiddenapp.data

import com.aurora.calculatorvault.feature.hiddenapp.domain.AppLaunchResult
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppRuntime
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppRuntimeInfo
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppRepositoryTest {

    @Test
    fun `add removes duplicates and skips packages no longer launchable`() = runTest {
        val installed = mapOf(
            "one" to InstalledApp("one", "One"),
            "two" to InstalledApp("two", "Two"),
        )
        val store = FakeStore()
        val repository = repository(installed, store) { 1234L }

        val count = repository.addApps(
            listOf(
                InstalledApp("one", "Old"),
                InstalledApp("one", "Duplicate"),
                InstalledApp("missing", "Missing"),
                InstalledApp("two", "Two"),
            ),
        )

        assertEquals(2, count)
        assertEquals(listOf("one", "two"), store.entities.value.map { it.packageName })
        assertEquals(listOf(0, 1), store.entities.value.map { it.sortOrder })
    }

    @Test
    fun `observation refreshes metadata and represents unavailable app`() = runTest {
        val store = FakeStore(
            entity("installed", "Old", 0),
            entity("removed", "Saved", 1),
        )
        val runtime = FakeRuntime(
            mutableMapOf(
                "installed" to InstalledAppRuntimeInfo(
                    "installed",
                    "Current",
                    InstalledAppAvailability.Available,
                ),
                "removed" to InstalledAppRuntimeInfo(
                    "removed",
                    null,
                    InstalledAppAvailability.NotInstalled,
                ),
            ),
        )
        val repository = HiddenAppRepository(
            scanner = FakeScanner(emptyMap()),
            runtime = runtime,
            store = store,
        )

        val apps = repository.observeHiddenApps().first()

        assertEquals("Current", apps[0].appName)
        assertEquals(InstalledAppAvailability.Available, apps[0].availability)
        assertEquals("Saved", apps[1].appName)
        assertEquals(InstalledAppAvailability.NotInstalled, apps[1].availability)
    }

    @Test
    fun `recent stream is ordered limited and clear preserves hidden records`() = runTest {
        val store = FakeStore(
            entity("a", "A", 0, lastOpenedAt = 10, openCount = 1),
            entity("b", "B", 1, lastOpenedAt = 30, openCount = 3),
            entity("c", "C", 2, lastOpenedAt = 20, openCount = 2),
        )
        val installed = listOf("a", "b", "c").associateWith {
            InstalledApp(it, it.uppercase())
        }
        val repository = repository(installed, store)

        assertEquals(
            listOf("b", "c"),
            repository.observeRecentApps(2).first().map { it.packageName },
        )
        assertEquals(3, repository.clearRecentHistory())
        assertEquals(3, store.entities.value.size)
        assertTrue(store.entities.value.all { it.lastOpenedAt == null && it.openCount == 0 })
    }

    @Test
    fun `mark opened updates time and increments only one unique record`() = runTest {
        val store = FakeStore(entity("one", "One", 0))
        val repository = repository(mapOf("one" to InstalledApp("one", "One")), store)

        assertTrue(repository.markAppOpened("one", 10))
        assertTrue(repository.markAppOpened("one", 20))

        val saved = store.entities.value.single()
        assertEquals(20L, saved.lastOpenedAt)
        assertEquals(2, saved.openCount)
    }

    @Test
    fun `remove deletes local row and its recent information only`() = runTest {
        val store = FakeStore(entity("one", "One", 0, 10, 2))
        val installed = mapOf("one" to InstalledApp("one", "One"))
        val repository = repository(installed, store)

        assertTrue(repository.removeApp("one"))
        assertTrue(installed.containsKey("one"))
        assertTrue(store.entities.value.isEmpty())
    }

    private fun repository(
        installed: Map<String, InstalledApp>,
        store: FakeStore,
        now: () -> Long = { 1L },
    ) = HiddenAppRepository(
        scanner = FakeScanner(installed),
        runtime = FakeRuntime(
            installed.mapValuesTo(mutableMapOf()) { (packageName, app) ->
                InstalledAppRuntimeInfo(
                    packageName,
                    app.appName,
                    InstalledAppAvailability.Available,
                )
            },
        ),
        store = store,
        currentTimeMillis = now,
    )

    private class FakeScanner(
        private val installed: Map<String, InstalledApp>,
    ) : InstalledAppScanner {
        override suspend fun scan() = installed.values.toList()
        override suspend fun resolve(packageName: String) = installed[packageName]
    }

    private class FakeRuntime(
        private val entries: MutableMap<String, InstalledAppRuntimeInfo>,
    ) : HiddenAppRuntime {
        override suspend fun resolve(packageName: String) =
            entries[packageName] ?: InstalledAppRuntimeInfo(
                packageName,
                null,
                InstalledAppAvailability.NotInstalled,
            )

        override suspend fun launch(packageName: String) = AppLaunchResult.Success
    }

    private class FakeStore(
        vararg initial: HiddenAppEntity,
    ) : HiddenAppStore {
        val entities = MutableStateFlow(initial.toList())

        override fun observeAll() = entities
        override fun observeRecent(limit: Int) = entities.map { rows ->
            rows.filter { it.lastOpenedAt != null }
                .sortedByDescending { it.lastOpenedAt }
                .take(limit)
        }
        override fun observePackageNames() =
            entities.map { rows -> rows.map(HiddenAppEntity::packageName).toSet() }

        override suspend fun addUnique(apps: List<InstalledApp>, addedAt: Long): Int {
            val existing = entities.value.map(HiddenAppEntity::packageName).toSet()
            var order = (entities.value.maxOfOrNull(HiddenAppEntity::sortOrder) ?: -1) + 1
            val additions = apps.distinctBy(InstalledApp::packageName)
                .filterNot { it.packageName in existing }
                .map {
                    HiddenAppEntity(
                        packageName = it.packageName,
                        appNameSnapshot = it.appName,
                        addedAt = addedAt,
                        sortOrder = order++,
                    )
                }
            entities.value += additions
            return additions.size
        }

        override suspend fun remove(packageName: String): Boolean {
            val before = entities.value.size
            entities.value = entities.value.filterNot { it.packageName == packageName }
            return entities.value.size < before
        }

        override suspend fun markOpened(packageName: String, openedAt: Long): Boolean {
            var changed = false
            entities.value = entities.value.map {
                if (it.packageName == packageName) {
                    changed = true
                    it.copy(lastOpenedAt = openedAt, openCount = it.openCount + 1)
                } else {
                    it
                }
            }
            return changed
        }

        override suspend fun clearRecentHistory(): Int {
            val count = entities.value.count { it.lastOpenedAt != null || it.openCount != 0 }
            entities.value = entities.value.map { it.copy(lastOpenedAt = null, openCount = 0) }
            return count
        }
    }

    private fun entity(
        packageName: String,
        name: String,
        sortOrder: Int,
        lastOpenedAt: Long? = null,
        openCount: Int = 0,
    ) = HiddenAppEntity(
        packageName = packageName,
        appNameSnapshot = name,
        addedAt = sortOrder.toLong(),
        sortOrder = sortOrder,
        lastOpenedAt = lastOpenedAt,
        openCount = openCount,
    )
}
