package com.aurora.calculatorvault.feature.login

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aurora.calculatorvault.feature.login.data.LoginPromptPreferences
import com.aurora.calculatorvault.feature.login.presentation.LoginPromptViewModel
import com.aurora.calculatorvault.feature.login.presentation.LoginSource
import com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LoginPromptTest {
    @Test
    fun `first home opens once and settings can reopen with fresh consent`() = runTest {
        withPreferences { preferences ->
            val model = LoginPromptViewModel(preferences, unconfiguredWeChatAuthorization())
            model.onHomeEntered()
            assertEquals(LoginSource.FirstHome, model.state.value.source)
            assertFalse(model.state.value.agreementAccepted)
            model.setAgreementAccepted(true)
            model.dismiss()
            model.onHomeEntered()
            assertNull(model.state.value.source)
            model.open(LoginSource.Settings)
            assertEquals(LoginSource.Settings, model.state.value.source)
            assertFalse(model.state.value.agreementAccepted)
            assertEquals(WeChatAuthorizationStatus.Idle, model.authorizationState.value)
        }
    }

    @Test
    fun `unchecked submit is blocked and checked submit reports missing AppID without login`() = runTest {
        withPreferences { preferences ->
            val model = LoginPromptViewModel(preferences, unconfiguredWeChatAuthorization())
            model.open(LoginSource.Settings)
            model.requestWeChatLogin()
            assertTrue(model.state.value.agreementRequired)
            assertEquals(WeChatAuthorizationStatus.Idle, model.authorizationState.value)
            model.setAgreementAccepted(true)
            model.requestWeChatLogin()
            assertFalse(model.state.value.agreementRequired)
            assertEquals(WeChatAuthorizationStatus.AppIdMissing, model.authorizationState.value)
            assertEquals(LoginSource.Settings, model.state.value.source)
            model.setAgreementAccepted(false)
            model.requestWeChatLogin()
            assertTrue(model.state.value.agreementRequired)
            assertEquals(WeChatAuthorizationStatus.Idle, model.authorizationState.value)
        }
    }

    @Test
    fun `home prompt remains consumed after datastore and viewmodel restart`() = runTest {
        val file = temporaryPreferencesFile()
        try {
            withStore(file) { preferences ->
                val first = LoginPromptViewModel(preferences, unconfiguredWeChatAuthorization())
                first.onHomeEntered()
                assertEquals(LoginSource.FirstHome, first.state.value.source)
                first.dismiss()
            }
            withStore(file) { preferences ->
                val restarted = LoginPromptViewModel(preferences, unconfiguredWeChatAuthorization())
                restarted.onHomeEntered()
                assertNull(restarted.state.value.source)
                restarted.open(LoginSource.Settings)
                assertEquals(LoginSource.Settings, restarted.state.value.source)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `repeated home entry while dialog is open does not reset agreement`() = runTest {
        withPreferences { preferences ->
            val model = LoginPromptViewModel(preferences, unconfiguredWeChatAuthorization())
            model.onHomeEntered()
            model.setAgreementAccepted(true)
            model.onHomeEntered()
            assertTrue(model.state.value.agreementAccepted)
            assertEquals(LoginSource.FirstHome, model.state.value.source)
        }
    }

    private fun temporaryPreferencesFile() = File(
        System.getProperty("java.io.tmpdir"),
        "login-prompt-${System.nanoTime()}.preferences_pb",
    )

    @Test
    fun `SDK is never invoked before the login agreement is accepted`() = runTest {
        withPreferences { preferences ->
            val sdk = FakeWeChatSdk()
            val auth = com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationCoordinator(
                sdk, MemoryWeChatStore(), PendingWeChatServerLoginEntry(),
            )
            val model = LoginPromptViewModel(preferences, auth)
            model.requestWeChatLogin()
            model.open(LoginSource.Settings)
            model.requestWeChatLogin()
            assertEquals(0, sdk.prepareCount)
            model.setAgreementAccepted(true)
            model.requestWeChatLogin()
            model.requestWeChatLogin()
            assertEquals(1, sdk.sendCount)
            assertEquals(WeChatAuthorizationStatus.Authorizing, model.authorizationState.value)
            model.dismiss()
            auth.onAuthorizationResponse(0, sdk.sentState, "late-test-code")
            assertNull(model.state.value.source)
            assertEquals(WeChatAuthorizationStatus.Idle, model.authorizationState.value)
        }
    }

    @Test
    fun `authorization code does not close prompt or create a signed in state`() = runTest {
        withPreferences { preferences ->
            val sdk = FakeWeChatSdk()
            val store = MemoryWeChatStore()
            var calls = 0
            val auth = com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationCoordinator(
                sdk, store, { calls++ },
            )
            val model = LoginPromptViewModel(preferences, auth)
            model.open(LoginSource.Settings)
            model.setAgreementAccepted(true)
            model.requestWeChatLogin()
            auth.onAuthorizationResponse(0, sdk.sentState, "test-only-code")
            assertEquals(1, calls)
            assertEquals(WeChatAuthorizationStatus.CodeReceived, model.authorizationState.value)
            assertEquals(LoginSource.Settings, model.state.value.source)
            assertTrue(model.state.value.agreementAccepted)
            assertNull(store.pending)
        }
    }

    private suspend fun withPreferences(block: suspend (LoginPromptPreferences) -> Unit) {
        val file = temporaryPreferencesFile()
        try { withStore(file, block) } finally { file.delete() }
    }

    private suspend fun withStore(file: File, block: suspend (LoginPromptPreferences) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            block(LoginPromptPreferences(store))
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
        }
    }
}
