package com.aurora.calculatorvault.feature.applock.domain

import org.junit.Test
import org.junit.Assert.*

class AppLockNotificationCoordinatorTest {
    private class Store(var offered: Boolean = false) : NotificationPromptStore {
        var writes = 0
        var failWrite = false
        var failRead = false
        override fun wasOffered(): Boolean { check(!failRead); return offered }
        override fun markOffered(): Boolean { writes++; if (failWrite) return false; offered=true; return true }
    }
    private fun denied(api: Int = 33) = NotificationPermissionSnapshot(api,false,false)
    private fun granted(api: Int = 33) = NotificationPermissionSnapshot(api,true,true)

    @Test fun pageEntryAndResumeNeverPrompt() {
        val store=Store(); val flow=AppLockNotificationCoordinator(store)
        repeat(10) { flow.refresh(denied()) }
        assertFalse(flow.state.value.showExplanation); assertEquals(0,store.writes)
        assertFalse(flow.confirmRequest(denied()))
    }
    @Test fun explicitEnableOffersExplanationBeforeSystemRequest() {
        val store=Store(); val flow=AppLockNotificationCoordinator(store)
        flow.onLockEnabled(denied())
        assertTrue(flow.state.value.showExplanation); assertFalse(flow.state.value.requestInFlight)
        assertTrue(store.offered); assertEquals(1,store.writes)
        assertTrue(flow.confirmRequest(denied())); assertTrue(flow.state.value.requestInFlight)
        assertFalse(flow.confirmRequest(denied()))
    }
    @Test fun repeatedEnableDuringPromptDoesNotRepeatOffer() {
        val store=Store(); val flow=AppLockNotificationCoordinator(store)
        repeat(5) { flow.onLockEnabled(denied()) }
        assertEquals(1,store.writes); assertTrue(flow.state.value.showExplanation)
    }
    @Test fun allowanceCompletesWithoutBlockingOperation() {
        val flow=AppLockNotificationCoordinator(Store())
        flow.onLockEnabled(denied()); flow.confirmRequest(denied()); flow.onResult(granted())
        assertFalse(flow.state.value.requestInFlight); assertFalse(flow.state.value.showSettingsHint)
        flow.onLockEnabled(granted()); assertFalse(flow.state.value.showExplanation)
    }
    @Test fun denialLeadsToSettingsHintAndNeverReopensAutomatically() {
        val store=Store(); val flow=AppLockNotificationCoordinator(store)
        flow.onLockEnabled(denied()); flow.confirmRequest(denied()); flow.onResult(denied())
        assertTrue(flow.state.value.showSettingsHint); assertFalse(flow.state.value.requestInFlight)
        repeat(10) { flow.refresh(denied()); flow.onLockEnabled(denied()) }
        assertFalse(flow.state.value.showExplanation); assertFalse(flow.confirmRequest(denied()))
        assertEquals(1,store.writes)
    }
    @Test fun permanentlySuppressedSystemDialogAlsoUsesSettingsEntry() {
        val flow=AppLockNotificationCoordinator(Store(true))
        flow.onLockEnabled(denied()); assertTrue(flow.state.value.showSettingsHint)
        assertFalse(flow.confirmRequest(denied())); assertFalse(flow.state.value.showExplanation)
    }
    @Test fun skippingExplanationIsRememberedAcrossPageAndColdStart() {
        val store=Store(); val first=AppLockNotificationCoordinator(store)
        first.onLockEnabled(denied()); first.dismissExplanation()
        val restored=AppLockNotificationCoordinator(store); restored.onLockEnabled(denied())
        assertFalse(restored.state.value.showExplanation); assertTrue(restored.state.value.showSettingsHint)
        assertEquals(1,store.writes)
    }
    @Test fun sdk32AndBelowNeverRequestPostNotifications() {
        for (api in listOf(26,30,31,32)) {
            val store=Store(); val flow=AppLockNotificationCoordinator(store)
            flow.onLockEnabled(NotificationPermissionSnapshot(api,true,false))
            assertFalse(flow.state.value.showExplanation); assertFalse(flow.confirmRequest(denied(api)))
            assertEquals(0,store.writes); assertTrue(flow.state.value.showSettingsHint)
        }
    }
    @Test fun sdk33And37UseRuntimePermission() {
        for (api in listOf(33,37)) {
            val flow=AppLockNotificationCoordinator(Store()); flow.onLockEnabled(denied(api))
            assertTrue(flow.confirmRequest(denied(api)))
        }
    }
    @Test fun alreadyGrantedDoesNotOfferOrWritePromptRecord() {
        val store=Store(); val flow=AppLockNotificationCoordinator(store)
        flow.onLockEnabled(granted()); assertFalse(flow.state.value.showExplanation)
        assertEquals(0,store.writes)
    }
    @Test fun settingsGrantRefreshesStateWithoutNewPrompt() {
        val flow=AppLockNotificationCoordinator(Store(true)); flow.refresh(denied())
        assertTrue(flow.state.value.showSettingsHint); flow.refresh(granted())
        assertFalse(flow.state.value.showSettingsHint); assertFalse(flow.state.value.showExplanation)
    }
    @Test fun permissionGrantedElsewhereDuringExplanationDoesNotLaunchDuplicate() {
        val flow=AppLockNotificationCoordinator(Store()); flow.onLockEnabled(denied())
        assertFalse(flow.confirmRequest(granted())); assertFalse(flow.state.value.requestInFlight)
        assertFalse(flow.state.value.showExplanation)
    }
    @Test fun persistenceFailureFallsBackToSettingsWithoutNagging() {
        val store=Store().apply { failWrite=true }; val flow=AppLockNotificationCoordinator(store)
        repeat(5) { flow.onLockEnabled(denied()) }
        assertFalse(flow.state.value.showExplanation); assertTrue(flow.state.value.showSettingsHint)
        assertEquals(1,store.writes)
    }
    @Test fun unreadablePromptStoreDoesNotCrashOrAutomaticallyRequest() {
        val flow=AppLockNotificationCoordinator(Store().apply { failRead=true })
        flow.onLockEnabled(denied()); assertFalse(flow.state.value.showExplanation)
        assertTrue(flow.state.value.showSettingsHint)
    }
    @Test fun systemNotificationsDisabledWithRuntimeGrantUsesSettingsNotPermissionRequest() {
        val flow=AppLockNotificationCoordinator(Store())
        val snapshot=granted().copy(notificationsEnabled=false)
        flow.onLockEnabled(snapshot); assertTrue(flow.state.value.showSettingsHint)
        assertFalse(flow.state.value.showExplanation); assertFalse(flow.confirmRequest(snapshot))
    }
    @Test fun notificationDenialDoesNotDetermineAppLockProtectionStatus() {
        val flow=AppLockNotificationCoordinator(Store()); flow.onLockEnabled(denied())
        flow.confirmRequest(denied()); flow.onResult(denied())
        val app=com.aurora.calculatorvault.feature.applock.presentation.AppLockUiState(
            lockedPackages=setOf("test.package"),hasUsageAccess=true,hasOverlayPermission=true,isMonitorRunning=true)
        assertEquals(AppLockProtectionStatus.Protecting,app.protectionStatus)
    }
}
