package com.aurora.calculatorvault.core.privacy

import com.aurora.calculatorvault.core.datastore.security.*
import com.aurora.calculatorvault.core.security.*
import com.aurora.calculatorvault.feature.onboarding.data.*
import com.aurora.calculatorvault.feature.onboarding.domain.*
import com.aurora.calculatorvault.feature.trial.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrivacyConsentTest {
    private fun current() = SecurityPreferences(true, PrivacyPolicy.CURRENT_VERSION, 123L)
    private class Source(var value: SecurityPreferences) : SecurityPreferencesDataSource {
        var failWrite = false
        var repairs = 0
        override suspend fun read() = value
        override suspend fun acceptPrivacy(version: String, acceptedAt: Long) {
            check(!failWrite)
            value = value.copy(privacyAccepted = true, privacyVersion = version, privacyAcceptedAt = acceptedAt)
        }
        override suspend fun withdrawPrivacy() { value = value.copy(privacyAccepted = false, privacyVersion = null, privacyAcceptedAt = null) }
        override suspend fun savePasswordInitialization(result: PasswordHashResult, createdAt: Long) = error("must preserve password")
        override suspend fun replacePassword(result: PasswordHashResult, updatedAt: Long) = error("must preserve password")
        override suspend fun repairIncompletePasswordSetup() { repairs++ }
    }
    private fun repository(source: Source) = OnboardingRepository(source, Pbkdf2PasswordHasher(), currentTimeMillis = { 456L })

    @Test fun freshInstallRequiresConsent() = runTest {
        val gate=PrivacyConsentGate { SecurityPreferences() }
        assertFalse(gate.check()); assertFalse(gate.isAllowedNow())
        assertEquals(StartupDestination.PrivacyConsent,StartupDestinationResolver.resolve(SecurityPreferences()))
    }
    @Test fun firstExplicitAcceptanceSavesVersionAndTime() = runTest {
        val source=Source(SecurityPreferences()); val gate=PrivacyConsentGate(source::read)
        assertEquals(OnboardingResult.Success(Unit),repository(source).acceptPrivacy())
        assertTrue(gate.check()); assertEquals(PrivacyPolicy.CURRENT_VERSION,source.value.privacyVersion)
        assertEquals(456L,source.value.privacyAcceptedAt)
    }
    @Test fun legacyAcceptedRecordIsNotMigratedImplicitly() = runTest {
        val source=Source(current().copy(privacyVersion="1.0",passwordConfigured=true,passwordHash="preserve-existing"))
        val before=source.value
        assertEquals(OnboardingResult.Success(StartupDestination.PrivacyConsent),repository(source).resolveStartupDestination())
        assertEquals(before,source.value); assertEquals(0,source.repairs)
    }
    @Test fun explicitReacceptanceOnlyChangesPrivacyFields() = runTest {
        val source=Source(current().copy(privacyVersion="1.0",passwordHash="keep",passwordSalt="keep-salt",onboardingCompleted=true))
        val before=source.value
        repository(source).acceptPrivacy()
        assertEquals(before.copy(privacyVersion=PrivacyPolicy.CURRENT_VERSION,privacyAcceptedAt=456L),source.value)
        assertTrue(PrivacyPolicy.hasValidConsent(source.value))
    }
    @Test fun legacyRejectionStaysBlockedAcrossColdRead() = runTest {
        val source=Source(current().copy(privacyVersion="1.0"))
        // Decline exits without recording acceptance; simulate a fresh process.
        repeat(2) { assertFalse(PrivacyConsentGate(source::read).check()) }
        assertEquals("1.0",source.value.privacyVersion)
    }
    @Test fun missingOrUnknownVersionsAndInvalidTimestampAreDenied() {
        for (value in listOf(current().copy(privacyVersion=null),current().copy(privacyVersion="future"),
            current().copy(privacyAccepted=false),current().copy(privacyAcceptedAt=null),current().copy(privacyAcceptedAt=0))) {
            assertFalse(PrivacyPolicy.hasValidConsent(value))
        }
    }
    @Test fun failedAcceptanceDoesNotGrantConsent() = runTest {
        val source=Source(current().copy(privacyVersion="1.0")).apply { failWrite=true }
        assertEquals(OnboardingResult.Failure(OnboardingFailure.PrivacySaveFailed),repository(source).acceptPrivacy())
        assertFalse(PrivacyConsentGate(source::read).check())
    }
    @Test fun mismatchedVersionBlocksSdkWorkAndEvents() = runTest {
        val gate=PrivacyConsentGate { current().copy(privacyVersion="1.0") }; var sdkCalls=0
        assertFalse(gate.check()); gate.runIfAllowed { sdkCalls++ }
        assertEquals(0,sdkCalls)
    }
    @Test fun withdrawalBlocksEventsAndCannotBeReenabledInSameProcess() = runTest {
        val source=Source(current()); val gate=PrivacyConsentGate(source::read); assertTrue(gate.check())
        gate.revoke(); source.withdrawPrivacy(); assertFalse(gate.check())
        repository(source).acceptPrivacy(); assertFalse(gate.check())
        var called=false; gate.runIfAllowed { called=true }; assertFalse(called)
        assertTrue(PrivacyConsentGate(source::read).check()) // replacement process may re-check explicit acceptance
    }
    @Test fun lateConsentReadCannotOverrideWithdrawal() = runTest {
        val response=CompletableDeferred<SecurityPreferences>(); val gate=PrivacyConsentGate { response.await() }
        var allowed=true
        val job=launch { allowed=gate.check() }; runCurrent(); gate.revoke()
        response.complete(current()); job.join()
        assertFalse(allowed); assertFalse(gate.isAllowedNow())
    }
    @Test fun readFailureFailsClosed() = runTest {
        val gate=PrivacyConsentGate { throw java.io.IOException() }
        assertFalse(gate.check()); assertFalse(gate.isAllowedNow())
    }
    @Test fun oldVersionBlocksTrialIdentityAndUploadEvenWhenBuildGateEnabled() = runTest {
        val clock=DeviceTrialTest.Clock(); val store=DeviceTrialTest.Store(); val api=DeviceTrialTest.Api(clock)
        val identity=DeviceTrialTest.FakeDeviceIdentityProvider()
        val gate=PrivacyConsentGate { current().copy(privacyVersion="1.0") }
        val trial=DeviceTrialRepository(store,clock,identity,api,backgroundScope,gate::check,{true})
        trial.sync(); runCurrent(); assertEquals(0,identity.calls); assertEquals(0,api.calls)
    }
    @Test fun reacceptanceDoesNotResetTrialOriginOrRemainingCap() = runTest {
        val source=Source(current().copy(privacyVersion="1.0")); val gate=PrivacyConsentGate(source::read)
        val clock=DeviceTrialTest.Clock(); val store=DeviceTrialTest.Store(); val api=DeviceTrialTest.Api(clock)
        val trial=DeviceTrialRepository(store,clock,DeviceTrialTest.FakeDeviceIdentityProvider(),api,backgroundScope,gate::check,{true})
        trial.sync(); runCurrent(); val origin=store.value!!.firstOpened
        clock.advance(3600000); repository(source).acceptPrivacy(); trial.sync(); runCurrent()
        assertEquals(origin,store.value!!.firstOpened); assertEquals(TRIAL_MILLIS-3600000,trial.state.value.remainingMillis)
    }
    @Test fun lateTrialReplyAfterWithdrawalCannotConfirmState() = runTest {
        val gate=PrivacyConsentGate { current() }; val clock=DeviceTrialTest.Clock(); val store=DeviceTrialTest.Store()
        val api=DeviceTrialTest.Api(clock).apply { this.gate=CompletableDeferred() }
        val trial=DeviceTrialRepository(store,clock,DeviceTrialTest.FakeDeviceIdentityProvider(),api,backgroundScope,gate::check,{true})
        trial.sync(); runCurrent(); gate.revoke(); api.gate!!.complete(Unit); runCurrent()
        assertFalse(store.value!!.verified); assertNotEquals(TrialStatus.ACTIVE,trial.state.value.status)
    }
    @Test fun wechatStartAndCallbackAreBlockedByRevokedGate() = runTest {
        val gate=PrivacyConsentGate { current() }; assertTrue(gate.check())
        val sdk=com.aurora.calculatorvault.feature.login.FakeWeChatSdk()
        val store=com.aurora.calculatorvault.feature.login.MemoryWeChatStore(); var handoffs=0
        val auth=com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationCoordinator(sdk,store,
            com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginEntry { handoffs++ },consentAllowed=gate::isAllowedNow)
        auth.start(); val state=sdk.sentState; gate.revoke()
        auth.onAuthorizationResponse(0,state,"test-code"); auth.start()
        assertEquals(0,handoffs); assertEquals(1,sdk.sendCount)
    }
    @Test fun lateLoginReplyCannotAuthenticateAfterWithdrawal() = runTest {
        val gate=PrivacyConsentGate { current() }
        val response=CompletableDeferred<com.aurora.calculatorvault.feature.login.session.LoginResult>()
        var saved:com.aurora.calculatorvault.feature.login.session.SessionCredential?=null
        var revokes=0
        val store=object:com.aurora.calculatorvault.feature.login.session.TokenStore {
            override fun read()=saved
            override fun write(value:com.aurora.calculatorvault.feature.login.session.SessionCredential) { saved=value }
            override fun clear() { saved=null }
        }
        val api=object:com.aurora.calculatorvault.feature.login.session.AuthApi {
            override suspend fun login(code:String)=response.await()
            override suspend fun me(token:String)=com.aurora.calculatorvault.feature.login.session.ServerAccount(1)
            override suspend fun logout(token:String) { revokes++ }
        }
        val account=com.aurora.calculatorvault.feature.login.session.AccountSessionRepository(api,store,backgroundScope,gate::check)
        account.acceptAuthorization(com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest("test-code"))
        runCurrent(); gate.revoke()
        response.complete(com.aurora.calculatorvault.feature.login.session.LoginResult(
            com.aurora.calculatorvault.feature.login.session.SessionCredential("test-token",java.time.Instant.now().plusSeconds(3600)),
            com.aurora.calculatorvault.feature.login.session.ServerAccount(1)))
        runCurrent(); assertNull(saved)
        assertEquals(com.aurora.calculatorvault.feature.login.session.AccountSession.Guest,account.state.value)
        assertEquals(1,revokes)
    }

}
