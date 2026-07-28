package com.aurora.calculatorvault.feature.hiddenapp.data

import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppRepositoryTest {

    @Test
    fun `add removes duplicate selections and skips packages no longer launchable`() = runTest {
        val scanner = FakeScanner(
            installed = mapOf(
                "one" to InstalledApp("one", "One"),
                "two" to InstalledApp("two", "Two"),
            ),
        )
        val store = FakeStore()
        val repository = HiddenAppRepository(scanner, store) { 1234L }

        val count = repository.addApps(
            listOf(
                InstalledApp("one", "Old One"),
                InstalledApp("one", "Duplicate"),
                InstalledApp("missing", "Missing"),
                InstalledApp("two", "Two"),
            ),
        )

        assertEquals(2, count)
        assertEquals(listOf("one", "two"), store.entities.value.map { it.packageName })
        assertEquals(listOf(0, 1), store.entities.value.map { it.sortOrder })
        assertEquals(listOf(1234L, 1234L), store.entities.value.map { it.addedAt })
    }

    @Test
    fun `existing package is ignored without changing its timestamp or sort order`() = runTest {
        val store = FakeStore(
            HiddenAppEntity(
                id = 1,
                packageName = "one",
                appNameSnapshot = "One",
                addedAt = 10,
                sortOrder = 4,
            ),
        )
        val scanner = FakeScanner(
            installed = mapOf(
                "one" to InstalledApp("one", "One New"),
                "two" to InstalledApp("two", "Two"),
            ),
        )
        val repository = HiddenAppRepository(scanner, store) { 99L }

        assertEquals(
            1,
            repository.addApps(
                listOf(InstalledApp("one", "One"), InstalledApp("two", "Two")),
            ),
        )

        assertEquals(10, store.entities.value.first { it.packageName == "one" }.addedAt)
        assertEquals(4, store.entities.value.first { it.packageName == "one" }.sortOrder)
        assertEquals(5, store.entities.value.first { it.packageName == "two" }.sortOrder)
    }

    @Test
    fun `observation uses current name and falls back to snapshot after uninstall`() = runTest {
        val store = FakeStore(
            HiddenAppEntity(
                packageName = "installed",
                appNameSnapshot = "Old name",
                addedAt = 1,
                sortOrder = 0,
            ),
            HiddenAppEntity(
                packageName = "removed",
                appNameSnapshot = "Saved name",
                addedAt = 2,
                sortOrder = 1,
            ),
        )
        val repository = HiddenAppRepository(
            FakeScanner(mapOf("installed" to InstalledApp("installed", "Current name"))),
            store,
        )

        val apps = repository.observeHiddenApps().first()

        assertEquals("Current name", apps[0].appName)
        assertTrue(apps[0].isInstalled)
        assertEquals("Saved name", apps[1].appName)
        assertFalse(apps[1].isInstalled)
    }

    @Test
    fun `remove only deletes local record`() = runTest {
        val store = FakeStore(
            HiddenAppEntity(
                packageName = "one",
                appNameSnapshot = "One",
                addedAt = 1,
                sortOrder = 0,
            ),
        )
        val scanner = FakeScanner(mapOf("one" to InstalledApp("one", "One")))
        val repository = HiddenAppRepository(scanner, store)

        assertTrue(repository.removeApp("one"))
        assertTrue(scanner.installed.containsKey("one"))
        assertTrue(store.entities.value.isEmpty())
    }

    private class FakeScanner(
        val installed: Map<String, InstalledApp>,
    ) : InstalledAppScanner {
        override suspend fun scan(): List<InstalledApp> = installed.values.toList()
        override suspend fun resolve(packageName: String): InstalledApp? = installed[packageName]
    }

    private class FakeStore(
        vararg initial: HiddenAppEntity,
    ) : HiddenAppStore {
        val entities = MutableStateFlow(initial.toList())

        override fun observeAll() = entities
        override fun observePackageNames() =
            MutableStateFlow(entities.value.map(HiddenAppEntity::packageName).toSet())

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
    }
}
