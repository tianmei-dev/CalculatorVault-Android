package com.aurora.calculatorvault.feature.hiddenapp.domain

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchEligibilityTest {
    @Test fun defaultEnabledApplicationCanResolveEntry() {
        assertTrue(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, true))
        assertFalse(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, false))
    }
    @Test fun enabledApplicationCanResolveEntry() {
        assertTrue(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, true))
    }
    @Test fun explicitlyDisabledApplicationIsBlockedEvenWithEnabledInfo() {
        assertFalse(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, true))
    }
    @Test fun userDisabledApplicationIsBlockedEvenWithEnabledInfo() {
        assertFalse(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, true))
    }
    @Test fun temporarilyDisabledApplicationCanResolveEntryEvenWithDisabledInfo() {
        assertTrue(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, false))
        assertTrue(canAttemptAppLaunch(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, true))
    }
}
