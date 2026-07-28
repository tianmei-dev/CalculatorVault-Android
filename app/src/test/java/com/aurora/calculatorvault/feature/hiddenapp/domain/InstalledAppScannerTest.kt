package com.aurora.calculatorvault.feature.hiddenapp.domain

import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledAppScannerTest {

    @Test
    fun `scan retains only enabled launchable third party apps and removes duplicates`() = runTest {
        val source = FakeSource(
            listOf(
                candidate("third.party", "Alpha"),
                candidate("system", "System", system = true),
                candidate("updated.system", "Updated", updatedSystem = true),
                candidate(OWN_PACKAGE, "Self"),
                candidate("disabled", "Disabled", enabled = false),
                candidate("instant", "Instant", instant = true),
                candidate("no.launcher", "No launcher", launchable = false),
                candidate("third.party", "Duplicate"),
            ),
        )

        val result = scanner(source).scan()

        assertEquals(listOf(InstalledApp("third.party", "Alpha")), result)
    }

    @Test
    fun `scan sorts application names with configured collator`() = runTest {
        val source = FakeSource(
            listOf(
                candidate("z", "Zulu"),
                candidate("a", "alpha"),
                candidate("b", "Bravo"),
            ),
        )

        assertEquals(
            listOf("alpha", "Bravo", "Zulu"),
            scanner(source).scan().map(InstalledApp::appName),
        )
    }

    @Test
    fun `resolve applies the same eligibility rules as scan`() = runTest {
        val source = FakeSource(
            listOf(
                candidate("valid", "Valid"),
                candidate("system", "System", system = true),
            ),
        )

        assertEquals(InstalledApp("valid", "Valid"), scanner(source).resolve("valid"))
        assertNull(scanner(source).resolve("system"))
        assertNull(scanner(source).resolve("missing"))
    }

    @Test
    fun `resolve falls back to launcher query when direct package resolution is unavailable`() =
        runTest {
            val expected = candidate("valid", "Launcher label")
            val source = object : LauncherAppSource {
                override fun queryLauncherApps(): List<LauncherAppCandidate> = listOf(expected)
                override fun resolve(packageName: String): LauncherAppCandidate? = null
            }

            assertEquals(
                InstalledApp("valid", "Launcher label"),
                scanner(source).resolve("valid"),
            )
        }

    @Test
    fun `resolve falls back when direct package resolution is not launcher eligible`() = runTest {
        val eligible = candidate("valid", "Launcher label")
        val source = object : LauncherAppSource {
            override fun queryLauncherApps(): List<LauncherAppCandidate> = listOf(eligible)
            override fun resolve(packageName: String): LauncherAppCandidate =
                eligible.copy(appName = "", hasLaunchIntent = false)
        }

        assertEquals(
            InstalledApp("valid", "Launcher label"),
            scanner(source).resolve("valid"),
        )
    }

    private fun scanner(source: LauncherAppSource) = FilteringInstalledAppScanner(
        source = source,
        ownPackageName = OWN_PACKAGE,
        dispatcher = Dispatchers.Unconfined,
        collator = Collator.getInstance(Locale.ENGLISH),
    )

    private class FakeSource(
        private val apps: List<LauncherAppCandidate>,
    ) : LauncherAppSource {
        override fun queryLauncherApps(): List<LauncherAppCandidate> = apps

        override fun resolve(packageName: String): LauncherAppCandidate? =
            apps.firstOrNull { it.packageName == packageName }
    }

    private companion object {
        const val OWN_PACKAGE = "com.aurora.calculatorvault"

        fun candidate(
            packageName: String,
            name: String,
            system: Boolean = false,
            updatedSystem: Boolean = false,
            enabled: Boolean = true,
            instant: Boolean = false,
            launchable: Boolean = true,
        ) = LauncherAppCandidate(
            packageName = packageName,
            appName = name,
            isSystemApp = system,
            isUpdatedSystemApp = updatedSystem,
            isEnabled = enabled,
            isInstantApp = instant,
            hasLaunchIntent = launchable,
        )
    }
}
