package com.aurora.calculatorvault.feature.hiddenapp.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHiddenAppRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val runtime = AndroidHiddenAppRuntime(
        context = context,
        packageManager = context.packageManager,
        ownPackageName = context.packageName,
    )

    @Test
    fun ownPackageCannotBeLaunchedThroughHiddenEntry() = runTest {
        assertEquals(AppLaunchResult.InvalidPackage, runtime.launch(context.packageName))
    }

    @Test
    fun missingPackageIsResolvedAndLaunchedAsNotInstalled() = runTest {
        val packageName = "com.aurora.calculatorvault.test.missing"

        assertEquals(
            InstalledAppAvailability.NotInstalled,
            runtime.resolve(packageName).availability,
        )
        assertEquals(AppLaunchResult.NotInstalled, runtime.launch(packageName))
    }
    @Test
    fun installedPackageWithoutLauncherIsNotReportedMissingOrDisabled() = runTest {
        assertEquals(InstalledAppAvailability.NoLauncher, runtime.resolve("android").availability)
        assertEquals(AppLaunchResult.NoLaunchIntent, runtime.launch("android"))
    }

    @Test
    fun deviceTargetUsesRuntimeAndPickerWithoutChangingEnabledState() = runTest {
        val target = InstrumentationRegistry.getArguments().getString("runtimeTarget")
        assumeTrue("Optional device target was not supplied", !target.isNullOrBlank())
        val packageName = requireNotNull(target)
        val before = context.packageManager.getApplicationEnabledSetting(packageName)
        assertEquals(InstalledAppAvailability.Available, runtime.resolve(packageName).availability)
        assertNotNull(FilteringInstalledAppScanner(
            AndroidLauncherAppSource(context.packageManager, includeTemporarilyDisabled = true), context.packageName,
        ).resolve(packageName))
        assertEquals(before, context.packageManager.getApplicationEnabledSetting(packageName))
    }
}
