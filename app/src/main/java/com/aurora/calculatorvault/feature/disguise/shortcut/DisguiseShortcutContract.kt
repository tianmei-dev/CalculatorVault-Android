package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Context
import android.content.Intent

object DisguiseShortcutContract {
    const val ACTION_OPEN_DISGUISE_SHORTCUT =
        "com.aurora.calculatorvault.action.OPEN_DISGUISE_SHORTCUT"
    const val ACTION_SHORTCUT_PINNED =
        "com.aurora.calculatorvault.action.DISGUISE_SHORTCUT_PINNED"
    const val EXTRA_SHORTCUT_ID =
        "com.aurora.calculatorvault.extra.DISGUISE_SHORTCUT_ID"

    fun createEntryIntent(context: Context, shortcutId: String): Intent =
        Intent(context, DisguiseShortcutEntryActivity::class.java).apply {
            action = ACTION_OPEN_DISGUISE_SHORTCUT
            putExtra(EXTRA_SHORTCUT_ID, shortcutId)
        }
}
