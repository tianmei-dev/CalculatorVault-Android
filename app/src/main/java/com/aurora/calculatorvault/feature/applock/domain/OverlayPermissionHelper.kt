package com.aurora.calculatorvault.feature.applock.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class OverlayPermissionHelper(
    private val context: Context,
) {
    fun isGranted(): Boolean = Settings.canDrawOverlays(context)

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.getOrElse {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
