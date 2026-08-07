package com.aurora.calculatorvault.feature.applock.domain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ForegroundAppResult {
    data class Success(val packageName: String) : ForegroundAppResult
    data object PermissionMissing : ForegroundAppResult
    data object Unavailable : ForegroundAppResult
}

interface ForegroundAppDetector {
    suspend fun currentForegroundPackage(): ForegroundAppResult
}

class UsageStatsForegroundAppDetector(
    private val context: Context,
    private val permissionHelper: UsageAccessPermissionHelper,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ForegroundAppDetector {

    override suspend fun currentForegroundPackage(): ForegroundAppResult = withContext(dispatcher) {
        if (!permissionHelper.hasUsageAccess()) return@withContext ForegroundAppResult.PermissionMissing
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext ForegroundAppResult.Unavailable
        try {
            val end = currentTimeMillis()
            val start = end - AppLockMonitorConfig.FOREGROUND_LOOKBACK_MILLIS
            val events = manager.queryEvents(start, end) ?: return@withContext ForegroundAppResult.Unavailable
            val event = UsageEvents.Event()
            var foregroundPackage: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    -> foregroundPackage = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    -> if (foregroundPackage == event.packageName) foregroundPackage = null
                }
            }
            foregroundPackage
                ?.takeIf(String::isNotBlank)
                ?.let(ForegroundAppResult::Success)
                ?: manager.latestUsedPackage(start, end)
        } catch (_: SecurityException) {
            ForegroundAppResult.PermissionMissing
        } catch (_: RuntimeException) {
            ForegroundAppResult.Unavailable
        }
    }

    private fun UsageStatsManager.latestUsedPackage(
        start: Long,
        end: Long,
    ): ForegroundAppResult {
        val latest = queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .filter { !it.packageName.isNullOrBlank() }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
        return latest
            ?.takeIf(String::isNotBlank)
            ?.let(ForegroundAppResult::Success)
            ?: ForegroundAppResult.Unavailable
    }
}
