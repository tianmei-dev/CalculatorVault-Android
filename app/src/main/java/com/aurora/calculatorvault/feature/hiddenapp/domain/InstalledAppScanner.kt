package com.aurora.calculatorvault.feature.hiddenapp.domain

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LauncherAppCandidate(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isEnabled: Boolean,
    val isInstantApp: Boolean,
    val hasLaunchIntent: Boolean,
)

interface LauncherAppSource {
    fun queryLauncherApps(): List<LauncherAppCandidate>
    fun resolve(packageName: String): LauncherAppCandidate?
}

interface InstalledAppScanner {
    suspend fun scan(): List<InstalledApp>
    suspend fun resolve(packageName: String): InstalledApp?
}

class FilteringInstalledAppScanner(
    private val source: LauncherAppSource,
    private val ownPackageName: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val collator: Collator = Collator.getInstance(Locale.getDefault()),
) : InstalledAppScanner {

    override suspend fun scan(): List<InstalledApp> = withContext(dispatcher) {
        source.queryLauncherApps()
            .asSequence()
            .filter(::isEligible)
            .distinctBy(LauncherAppCandidate::packageName)
            .map { InstalledApp(it.packageName, it.appName) }
            .sortedWith { left, right -> collator.compare(left.appName, right.appName) }
            .toList()
    }

    override suspend fun resolve(packageName: String): InstalledApp? = withContext(dispatcher) {
        val directlyResolved = runCatching { source.resolve(packageName) }.getOrNull()
        val candidate = directlyResolved?.takeIf(::isEligible) ?: run {
            runCatching {
                source.queryLauncherApps().firstOrNull { it.packageName == packageName }
            }.getOrNull()
        }
        candidate
            ?.takeIf(::isEligible)
            ?.let { InstalledApp(it.packageName, it.appName) }
    }

    private fun isEligible(candidate: LauncherAppCandidate): Boolean =
        candidate.packageName != ownPackageName &&
            candidate.packageName.isNotBlank() &&
            candidate.appName.isNotBlank() &&
            !candidate.isSystemApp &&
            !candidate.isUpdatedSystemApp &&
            candidate.isEnabled &&
            !candidate.isInstantApp &&
            candidate.hasLaunchIntent
}

class AndroidLauncherAppSource(
    private val packageManager: PackageManager,
) : LauncherAppSource {

    override fun queryLauncherApps(): List<LauncherAppCandidate> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
        return activities.mapNotNull { resolveInfo ->
            runCatching {
                val activityInfo = resolveInfo.activityInfo ?: return@runCatching null
                val applicationInfo = activityInfo.applicationInfo
                val packageName = applicationInfo.packageName
                LauncherAppCandidate(
                    packageName = packageName,
                    appName = resolveInfo.loadLabel(packageManager).toString().trim(),
                    isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isUpdatedSystemApp =
                        applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                    isEnabled = applicationInfo.enabled && activityInfo.enabled,
                    isInstantApp = packageManager.isInstantApp(packageName),
                    hasLaunchIntent = packageManager.getLaunchIntentForPackage(packageName) != null,
                )
            }.getOrNull()
        }
    }

    override fun resolve(packageName: String): LauncherAppCandidate? = runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return@runCatching null
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(launchIntent, 0)
        }
        val activityInfo = resolveInfo?.activityInfo
        LauncherAppCandidate(
            packageName = packageName,
            appName = resolveInfo
                ?.loadLabel(packageManager)
                ?.toString()
                ?.trim()
                .orEmpty()
                .ifBlank {
                    packageManager.getApplicationLabel(applicationInfo).toString().trim()
                },
            isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isUpdatedSystemApp =
                applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            isEnabled = applicationInfo.enabled && activityInfo?.enabled != false,
            isInstantApp = packageManager.isInstantApp(packageName),
            hasLaunchIntent = true,
        )
    }.getOrNull()
}
