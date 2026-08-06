package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisguiseShortcutEntryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val validId = "cv_disguise_123e4567-e89b-42d3-a456-426614174000"

    @Test
    fun shortcutIntent_isExplicitAndCarriesOnlyOpaqueId() {
        val intent = DisguiseShortcutContract.createEntryIntent(context, validId)

        assertNotNull(intent.component)
        assertEquals(DisguiseShortcutEntryActivity::class.java.name, intent.component?.className)
        assertEquals(DisguiseShortcutContract.ACTION_OPEN_DISGUISE_SHORTCUT, intent.action)
        assertEquals(setOf(DisguiseShortcutContract.EXTRA_SHORTCUT_ID), intent.extras?.keySet())
        assertEquals(validId, intent.getStringExtra(DisguiseShortcutContract.EXTRA_SHORTCUT_ID))
    }

    @Test
    fun shortcutEntryActivity_isIsolatedFromMainVaultTask() {
        val activityInfo = context.packageManager.getActivityInfo(
            DisguiseShortcutContract.createEntryIntent(context, validId).component!!,
            PackageManager.GET_META_DATA,
        )

        assertEquals("com.aurora.calculatorvault.disguise_entry", activityInfo.taskAffinity)
        assertTrue(activityInfo.flags and android.content.pm.ActivityInfo.FLAG_NO_HISTORY != 0)
        assertTrue(
            activityInfo.flags and android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0,
        )
    }
}
