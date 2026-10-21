package com.aurora.calculatorvault.feature.login.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.calculatorvault.feature.login.data.LoginPromptPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LoginSource { FirstHome, Settings, MembershipPurchase, SessionExpired }

data class LoginPromptState(
    val source: LoginSource? = null,
    val agreementAccepted: Boolean = false,
    val agreementRequired: Boolean = false,
    val placeholderVisible: Boolean = false,
)

/** Presentation only: never creates an account or an authenticated session. */
class LoginPromptViewModel(private val preferences: LoginPromptPreferences) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginPromptState())
    val state = mutableState.asStateFlow()

    suspend fun onHomeEntered() {
        if (preferences.claimFirstHomePrompt() && mutableState.value.source == null) {
            open(LoginSource.FirstHome)
        }
    }

    fun open(source: LoginSource) {
        mutableState.value = LoginPromptState(source = source)
    }

    fun dismiss() { mutableState.value = LoginPromptState() }

    fun setAgreementAccepted(accepted: Boolean) {
        mutableState.update {
            it.copy(agreementAccepted = accepted, agreementRequired = false, placeholderVisible = false)
        }
    }

    fun requestWeChatLogin() {
        mutableState.update {
            if (it.source == null) it
            else if (!it.agreementAccepted) it.copy(agreementRequired = true)
            // A6.1 integration boundary: replace this UI placeholder with an SDK event later.
            else it.copy(agreementRequired = false, placeholderVisible = true)
        }
    }

    class Factory(private val preferences: LoginPromptPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LoginPromptViewModel::class.java))
            return LoginPromptViewModel(preferences) as T
        }
    }
}
