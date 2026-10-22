package com.aurora.calculatorvault.feature.login.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.calculatorvault.feature.login.data.LoginPromptPreferences
import com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LoginSource { FirstHome, Settings, MembershipPurchase, SessionExpired }

data class LoginPromptState(
    val source: LoginSource? = null,
    val agreementAccepted: Boolean = false,
    val agreementRequired: Boolean = false,
)

/** Presentation only: never creates an account or an authenticated session. */
class LoginPromptViewModel(
    private val preferences: LoginPromptPreferences,
    private val authorization: WeChatAuthorizationCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginPromptState())
    val state = mutableState.asStateFlow()
    val authorizationState = authorization.status

    suspend fun onHomeEntered() {
        if (preferences.claimFirstHomePrompt() && mutableState.value.source == null) {
            open(LoginSource.FirstHome)
        }
    }

    fun open(source: LoginSource) {
        authorization.refresh()
        mutableState.value = LoginPromptState(source = source)
    }

    fun dismiss() {
        authorization.cancel()
        mutableState.value = LoginPromptState()
    }

    fun refreshAuthorization() = authorization.refresh()

    fun setAgreementAccepted(accepted: Boolean) {
        if (!accepted) authorization.cancel()
        mutableState.update {
            it.copy(agreementAccepted = accepted, agreementRequired = false)
        }
    }

    fun requestWeChatLogin() {
        val current = mutableState.value
        if (current.source == null) return
        if (!current.agreementAccepted) {
            mutableState.update { it.copy(agreementRequired = true) }
            return
        }
        mutableState.update { it.copy(agreementRequired = false) }
        authorization.start()
    }

    class Factory(
        private val preferences: LoginPromptPreferences,
        private val authorization: WeChatAuthorizationCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LoginPromptViewModel::class.java))
            return LoginPromptViewModel(preferences, authorization) as T
        }
    }
}
