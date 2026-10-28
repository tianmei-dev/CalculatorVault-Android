package com.aurora.calculatorvault.feature.applock.presentation

import android.Manifest
import android.app.NotificationManager
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aurora.calculatorvault.feature.applock.domain.NotificationPermissionSnapshot
import com.aurora.calculatorvault.feature.applock.domain.NotificationPromptStore

/** Stores only an anonymous local prompt flag; permission grants always come from Android. */
class AndroidAppLockNotifications(private val context: Context) : NotificationPromptStore {
    private val preferences by lazy { context.getSharedPreferences("app_lock_notification_prompt", Context.MODE_PRIVATE) }
    override fun wasOffered(): Boolean = try { preferences.getBoolean("offered", false) } catch (_: Exception) { true }
    override fun markOffered(): Boolean = try { preferences.edit().putBoolean("offered", true).commit() } catch (_: Exception) { false }

    fun snapshot(): NotificationPermissionSnapshot {
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val enabled = try {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(AppLockMonitorService.CHANNEL_ID)
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
        } catch (_: Exception) { false }
        return NotificationPermissionSnapshot(Build.VERSION.SDK_INT, granted, enabled)
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
