package com.aurora.calculatorvault.feature.applock.domain

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

fun interface AppLockPackagePolicyChecker {
    fun canBeLocked(packageName: String): Boolean
}

class AppLockPackagePolicy(
    private val packageManager: PackageManager,
    private val ownPackageName: String,
) : AppLockPackagePolicyChecker {
    override fun canBeLocked(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == ownPackageName) return false
        if (packageName == SYSTEM_UI_PACKAGE) return false
        if (packageName in launcherPackages()) return false
        if (packageName in settingsPackages()) return false
        return true
    }

    private fun launcherPackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()
        val candidates = runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        return (candidates + resolved).filterNotNull().toSet()
    }

    private fun settingsPackages(): Set<String> {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val resolved = runCatching {
            packageManager.resolveActivity(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()
        val candidates = runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(settingsIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        return (candidates + resolved + COMMON_SETTINGS_PACKAGES).filterNotNull().toSet()
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        val COMMON_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
        )
    }
}
