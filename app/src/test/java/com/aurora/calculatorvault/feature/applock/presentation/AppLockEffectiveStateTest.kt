package com.aurora.calculatorvault.feature.applock.presentation

import com.aurora.calculatorvault.feature.applock.domain.*
import org.junit.Assert.*
import org.junit.Test

class AppLockEffectiveStateTest {
    private fun configured()=AppLockUiState(
        apps=listOf(LockableApp("a","App A",true),LockableApp("b","App B",false)),
        lockedPackages=setOf("a"), protectionEntitled=true,
        hasUsageAccess=true,hasOverlayPermission=true,isMonitorRunning=true)
    @Test fun validTrialOrVipShowsEffectiveProtection() {
        val s=configured();assertTrue(s.visibleApps.first().locked);assertEquals(1,s.lockedCount)
        assertEquals(AppLockProtectionStatus.Protecting,s.protectionStatus)
    }
    @Test fun expiryOrLogoutClearsDisplayWithoutChangingConfiguration() {
        val s=configured().copy(protectionEntitled=false)
        assertFalse(s.visibleApps.first().locked);assertEquals(0,s.lockedCount)
        assertNotEquals(AppLockProtectionStatus.Protecting,s.protectionStatus)
        assertTrue(s.apps.first().locked);assertEquals(setOf("a"),s.lockedPackages)
    }
    @Test fun restoredMembershipRestoresOnlyUserEnabledEntries() {
        val s=configured().copy(protectionEntitled=false).copy(protectionEntitled=true)
        assertTrue(s.visibleApps[0].locked);assertFalse(s.visibleApps[1].locked);assertEquals(1,s.lockedCount)
    }
    @Test fun multipleEntriesExpireTogetherAndSearchDoesNotChangeTotal() {
        val s=configured().copy(apps=listOf(LockableApp("a","App A",true),LockableApp("b","App B",true)),lockedPackages=setOf("a","b"))
        assertEquals(2,s.copy(query="App A").lockedCount)
        val expired=s.copy(protectionEntitled=false,query="App A")
        assertEquals(1,expired.visibleApps.size);assertFalse(expired.visibleApps.single().locked);assertEquals(0,expired.lockedCount)
    }
    @Test fun pageRecreationCannotOptimisticallyDisplayProtection() {
        val s=AppLockUiState(apps=configured().apps,lockedPackages=setOf("a"))
        assertEquals(0,s.lockedCount);assertFalse(s.visibleApps.first().locked)
    }
    @Test fun trialToVipContinuityKeepsSelectionAndCount() {
        val s=configured();assertEquals(s.visibleApps,s.copy(protectionEntitled=true).visibleApps)
        assertEquals(1,s.copy(protectionEntitled=true).lockedCount)
    }
}
