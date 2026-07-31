package com.aurora.calculatorvault.feature.disguise.shortcut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat

class AndroidPinnedShortcutCreator(
    private val context: Context,
    private val iconFactory: DisguiseShortcutIconFactory,
) : PinnedShortcutCreator {
    override fun isSupported(): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    override suspend fun requestPinShortcut(
        request: PinShortcutRequest,
    ): PinShortcutRequestResult {
        val label = request.displayName.trim()
        if (label.isEmpty()) return PinShortcutRequestResult.InvalidConfiguration
        val icon = iconFactory.create(request.iconId)
            ?: return PinShortcutRequestResult.IconGenerationFailed

        return try {
            val entryIntent = Intent(context, DisguiseShortcutEntryActivity::class.java).apply {
                action = DisguiseShortcutContract.ACTION_OPEN_DISGUISE_SHORTCUT
                putExtra(DisguiseShortcutContract.EXTRA_SHORTCUT_ID, request.shortcutId)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, request.shortcutId)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(entryIntent)
                .build()
            val callbackIntent = Intent(context, ShortcutPinnedReceiver::class.java).apply {
                action = DisguiseShortcutContract.ACTION_SHORTCUT_PINNED
                putExtra(DisguiseShortcutContract.EXTRA_SHORTCUT_ID, request.shortcutId)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                request.shortcutId.hashCode(),
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (
                ShortcutManagerCompat.requestPinShortcut(
                    context,
                    shortcut,
                    callback.intentSender,
                )
            ) {
                PinShortcutRequestResult.RequestSubmitted
            } else {
                PinShortcutRequestResult.RequestRejectedImmediately
            }
        } catch (_: SecurityException) {
            PinShortcutRequestResult.SecurityBlocked
        } catch (_: IllegalArgumentException) {
            PinShortcutRequestResult.InvalidConfiguration
        } catch (_: Exception) {
            PinShortcutRequestResult.Failed
        }
    }
}

