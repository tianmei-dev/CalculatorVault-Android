package com.aurora.calculatorvault.feature.login

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aurora.calculatorvault.feature.login.data.LoginPromptPreferences
import com.aurora.calculatorvault.feature.login.presentation.LoginPromptViewModel
import com.aurora.calculatorvault.feature.login.presentation.LoginSource
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
            val model = LoginPromptViewModel(preferences)
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
            assertFalse(model.state.value.placeholderVisible)
        }
    }

    @Test
    fun `unchecked submit is blocked and checked submit only shows placeholder`() = runTest {
        withPreferences { preferences ->
            val model = LoginPromptViewModel(preferences)
            model.open(LoginSource.Settings)
            model.requestWeChatLogin()
            assertTrue(model.state.value.agreementRequired)
            assertFalse(model.state.value.placeholderVisible)
            model.setAgreementAccepted(true)
            model.requestWeChatLogin()
            assertFalse(model.state.value.agreementRequired)
            assertTrue(model.state.value.placeholderVisible)
            assertEquals(LoginSource.Settings, model.state.value.source)
            model.setAgreementAccepted(false)
            model.requestWeChatLogin()
            assertTrue(model.state.value.agreementRequired)
            assertFalse(model.state.value.placeholderVisible)
        }
    }

    @Test
    fun `home prompt remains consumed after datastore and viewmodel restart`() = runTest {
        val file = temporaryPreferencesFile()
        try {
            withStore(file) { preferences ->
                val first = LoginPromptViewModel(preferences)
                first.onHomeEntered()
                assertEquals(LoginSource.FirstHome, first.state.value.source)
                first.dismiss()
            }
            withStore(file) { preferences ->
                val restarted = LoginPromptViewModel(preferences)
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
            val model = LoginPromptViewModel(preferences)
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
