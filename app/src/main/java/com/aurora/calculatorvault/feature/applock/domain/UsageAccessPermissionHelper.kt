package com.aurora.calculatorvault.feature.applock.domain

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class UsageAccessPermissionHelper(
    private val context: Context,
) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED || canQueryUsageStats()
    }

    fun openUsageAccessSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: RuntimeException) {
        false
    }

    private fun canQueryUsageStats(): Boolean {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        val end = System.currentTimeMillis()
        val start = end - USAGE_ACCESS_CHECK_LOOKBACK_MILLIS
        return try {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).isNotEmpty()
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private companion object {
        const val USAGE_ACCESS_CHECK_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
