package com.aurora.calculatorvault.feature.hiddenapp.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface HiddenAppRuntime {
    suspend fun resolve(packageName: String): InstalledAppRuntimeInfo
    suspend fun launch(packageName: String): AppLaunchResult
    suspend fun openSettings(packageName: String): Boolean = false
}

/**
 * 单包状态读取和启动统一从这里进入；批量应用扫描仍由 InstalledAppScanner 负责。
 */
class AndroidHiddenAppRuntime(
    private val context: Context,
    private val packageManager: PackageManager,
    private val ownPackageName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : HiddenAppRuntime {

    override suspend fun resolve(packageName: String): InstalledAppRuntimeInfo =
        withContext(ioDispatcher) {
            inspect(packageName).info
        }

    override suspend fun launch(packageName: String): AppLaunchResult {
        if (packageName.isBlank() || packageName == ownPackageName) {
            return AppLaunchResult.InvalidPackage
        }
        val inspection = withContext(ioDispatcher) { inspect(packageName) }
        val intent = inspection.launchIntent ?: return inspection.info.availability.toLaunchResult()
        return withContext(mainDispatcher) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                AppLaunchResult.Success
            } catch (_: ActivityNotFoundException) {
                AppLaunchResult.ActivityNotFound
            } catch (_: SecurityException) {
                AppLaunchResult.SecurityBlocked
            } catch (_: RuntimeException) {
                AppLaunchResult.Failed
            }
        }
    }

    override suspend fun openSettings(packageName: String): Boolean =
        withContext(mainDispatcher) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData("package:$packageName".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            } catch (_: RuntimeException) {
                false
            }
        }

    private fun inspect(packageName: String): Inspection {
        if (packageName.isBlank() || packageName == ownPackageName) {
            return Inspection(
                InstalledAppRuntimeInfo(packageName, null, InstalledAppAvailability.Unknown),
                null,
            )
        }
        val applicationInfo = try {
            getApplicationInfo(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return Inspection(
                InstalledAppRuntimeInfo(packageName, null, InstalledAppAvailability.NotInstalled),
                null,
            )
        } catch (_: RuntimeException) {
            return Inspection(
                InstalledAppRuntimeInfo(packageName, null, InstalledAppAvailability.Unknown),
                null,
            )
        }
        val name = runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString().trim().ifBlank { null }
        }.getOrNull()
        if (!isEnabled(packageName, applicationInfo)) {
            return Inspection(
                InstalledAppRuntimeInfo(packageName, name, InstalledAppAvailability.Disabled),
                null,
            )
        }
        val launchIntent = runCatching {
            packageManager.getLaunchIntentForPackage(packageName) ?: fallbackLaunchIntent(packageName)
        }.getOrNull()
        return if (launchIntent == null) {
            Inspection(
                InstalledAppRuntimeInfo(packageName, name, InstalledAppAvailability.NoLauncher),
                null,
            )
        } else {
            Inspection(
                InstalledAppRuntimeInfo(packageName, name, InstalledAppAvailability.Available),
                launchIntent,
            )
        }
    }

    private fun isEnabled(packageName: String, info: ApplicationInfo): Boolean {
        if (!info.enabled) return false
        return when (
            runCatching { packageManager.getApplicationEnabledSetting(packageName) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        ) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            else -> true
        }
    }

    private fun fallbackLaunchIntent(packageName: String): Intent? {
        val query = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                query,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(query, 0)
        }
        val activity = activities.firstOrNull { it.activityInfo?.enabled == true }?.activityInfo
            ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(activity.packageName, activity.name)
    }

    private fun getApplicationInfo(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }

    private data class Inspection(
        val info: InstalledAppRuntimeInfo,
        val launchIntent: Intent?,
    )

    private fun InstalledAppAvailability.toLaunchResult(): AppLaunchResult = when (this) {
        InstalledAppAvailability.Available -> AppLaunchResult.Failed
        InstalledAppAvailability.NotInstalled -> AppLaunchResult.NotInstalled
        InstalledAppAvailability.Disabled -> AppLaunchResult.Disabled
        InstalledAppAvailability.NoLauncher -> AppLaunchResult.NoLaunchIntent
        InstalledAppAvailability.Unknown -> AppLaunchResult.Failed
    }
}
