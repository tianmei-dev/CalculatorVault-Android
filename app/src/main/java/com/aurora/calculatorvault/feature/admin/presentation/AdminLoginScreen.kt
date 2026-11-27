package com.aurora.calculatorvault.feature.admin.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.admin.AdminLoginResult
import com.aurora.calculatorvault.feature.admin.AdminSession
import com.aurora.calculatorvault.feature.admin.AdminSessionRepository
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.component.vaultGradientAction
import com.aurora.calculatorvault.ui.component.vaultSecondaryBackdrop
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.ui.message.LocalAppMessageController
import kotlinx.coroutines.launch

@Composable
fun AdminLoginScreen(
    repository: AdminSessionRepository,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit = onBack,
) {
    val session by repository.state.collectAsState()
    val active = session as? AdminSession.Active
    val messages = LocalAppMessageController.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var inFlight by remember { mutableStateOf(false) }

    val emptyCredentials = stringResource(R.string.admin_empty_credentials)
    val loginSuccess = stringResource(R.string.admin_login_success)
    val logoutSuccess = stringResource(R.string.admin_logout_success)
    val invalid = stringResource(R.string.admin_invalid_credentials)
    val disabled = stringResource(R.string.admin_account_disabled)
    val rateLimited = stringResource(R.string.admin_rate_limited)
    val unavailable = stringResource(R.string.admin_service_unavailable)

    fun submit() {
        if (inFlight) return
        if (username.isBlank() || password.isEmpty()) {
            messages.showWarning(emptyCredentials)
            return
        }
        inFlight = true
        scope.launch {
            when (val result = repository.login(username, password)) {
                is AdminLoginResult.Success -> {
                    password = ""
                    messages.showSuccess(loginSuccess)
                    onLoginSuccess()
                }
                AdminLoginResult.InvalidCredentials -> {
                    password = ""
                    messages.showWarning(invalid)
                }
                AdminLoginResult.AccountDisabled -> {
                    password = ""
                    messages.showWarning(disabled)
                }
                AdminLoginResult.RateLimited -> messages.showWarning(rateLimited)
                AdminLoginResult.ServiceUnavailable -> messages.showWarning(unavailable)
            }
            inFlight = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .vaultSecondaryBackdrop()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("admin_login_page"),
    ) {
        VaultTopAppBar(
            title = stringResource(R.string.admin_login_title),
            navigationIcon = {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                )
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(appPagePadding()),
        ) {
            VaultCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = VaultMainColors.Highlight,
                        modifier = Modifier.size(AppSpacing.xxl),
                    )
                    Text(
                        text = if (active != null) {
                            stringResource(R.string.admin_logged_in)
                        } else {
                            stringResource(R.string.admin_login_hint)
                        },
                        style = AppTextStyles.CardTitle,
                        color = VaultMainColors.TextPrimary,
                    )
                    if (active == null) {
                        Text(
                            text = stringResource(R.string.admin_login_entry_hint),
                            style = AppTextStyles.BodySecondary,
                            color = VaultMainColors.TextSecondary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.admin_account_label, active.username),
                            style = AppTextStyles.BodySecondary,
                            color = VaultMainColors.TextSecondary,
                        )
                    }
                    if (active != null) {
                        OutlinedButton(
                            onClick = {
                                repository.logout()
                                messages.showInfo(logoutSuccess)
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_logout")
                                .vaultGradientAction(secondary = true, shape = CircleShape),
                        ) {
                            Text(
                                stringResource(R.string.admin_logout_action),
                                style = AppTextStyles.Button,
                                color = VaultMainColors.Highlight,
                            )
                        }
                    } else {
                        AdminField(
                            value = username,
                            onValueChange = { username = it },
                            label = stringResource(R.string.admin_username),
                            enabled = !inFlight,
                            fieldTestTag = "admin_username",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        AdminField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.admin_password),
                            enabled = !inFlight,
                            fieldTestTag = "admin_password",
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = null,
                                        tint = VaultMainColors.TextSecondary,
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                        Button(
                            onClick = { submit() },
                            enabled = !inFlight,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AppSpacing.xs)
                                .testTag("admin_login_submit")
                                .vaultGradientAction(enabled = !inFlight, shape = CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.admin_login_action),
                                style = AppTextStyles.Button,
                                color = VaultMainColors.TextPrimary,
                            )
                        }
                    }
                }
            }
            if (active == null) {
                VaultIconButton(
                    icon = VaultIcons.Close,
                    contentDescription = stringResource(R.string.admin_close_login),
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun AdminField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    fieldTestTag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(fieldTestTag),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = AppTextStyles.Body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VaultMainColors.Highlight,
            cursorColor = VaultMainColors.Highlight,
            focusedLabelColor = VaultMainColors.Highlight,
            focusedTextColor = VaultMainColors.TextPrimary,
            unfocusedTextColor = VaultMainColors.TextPrimary,
        ),
    )
}
