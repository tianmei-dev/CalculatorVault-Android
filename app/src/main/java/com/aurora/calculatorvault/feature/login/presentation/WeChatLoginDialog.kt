package com.aurora.calculatorvault.feature.login.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationStatus
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultVisualAlertDialog
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultGradientAction
import com.aurora.calculatorvault.ui.component.vaultMainPanel

/** Shared UI for first-home, settings, purchase and session-expiry entry points. */
@Composable
fun WeChatLoginDialog(
    state: LoginPromptState,
    onAgreementChanged: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onOpenDocument: (LegalDocumentType) -> Unit,
    onDismiss: () -> Unit,
    authorizationStatus: WeChatAuthorizationStatus = WeChatAuthorizationStatus.Idle,
    sessionState: AccountSession = AccountSession.Guest,
) {
    val polished = useVaultDarkVisuals()
    val palette = MaterialTheme.colorScheme
    val titleColor = if (polished) VaultMainColors.TextPrimary else palette.onSurface
    val bodyColor = if (polished) VaultMainColors.TextSecondary else palette.onSurfaceVariant
    val linkColor = if (polished) VaultMainColors.Highlight else palette.primary
    val loginEnabled = authorizationStatus != WeChatAuthorizationStatus.Authorizing &&
        sessionState != AccountSession.Authenticating

    VaultVisualAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("wechat_login_dialog"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = AppShapes.ExtraLarge,
        emphasized = true,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            ) {
                val context = LocalContext.current
                val iconSize = with(LocalDensity.current) { AppDimensions.LargeButton.roundToPx() }
                // Render the existing launcher drawable, including adaptive icons, without
                // passing an unsupported AdaptiveIconDrawable to painterResource.
                val appIcon = remember(context, iconSize) {
                    requireNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
                        .toBitmap(iconSize, iconSize).asImageBitmap()
                }
                Box(
                    modifier = Modifier.size(AppDimensions.LargeButton).then(
                        if (polished) Modifier.vaultMainPanel(emphasized = true, shape = AppShapes.Medium)
                            .padding(AppSpacing.xxs) else Modifier,
                    ),
                ) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(AppShapes.Medium)
                            .testTag("wechat_login_app_icon"),
                    )
                }
                Text(
                    stringResource(R.string.wechat_login_welcome),
                    modifier = Modifier.fillMaxWidth(),
                    style = AppTextStyles.SectionTitle.copy(
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        lineBreak = LineBreak.Heading,
                    ),
                    color = titleColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.wechat_login_description),
                    modifier = Modifier.fillMaxWidth(),
                    style = AppTextStyles.Body.copy(lineBreak = LineBreak.Paragraph),
                    color = bodyColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when (sessionState) {
                        AccountSession.Authenticating -> "正在登录服务器…"
                        is AccountSession.Validating -> "正在校验账号…"
                        is AccountSession.TemporarilyUnavailable -> "网络暂不可用，账号待验证"
                        AccountSession.LoginFailed -> "登录失败，请重新发起微信授权"
                        AccountSession.StorageFailed -> "安全存储不可用，请重试"
                        AccountSession.SessionExpired -> "会话已失效，请重新登录"
                        else -> ""
                    },
                    color = bodyColor,
                    style = AppTextStyles.BodySecondary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Button(
                        onClick = onLogin,
                        enabled = loginEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.Button)
                            .testTag("wechat_login_button")
                            .then(
                                if (polished) Modifier.vaultGradientAction(loginEnabled, shape = AppShapes.Medium)
                                else Modifier,
                            ),
                        shape = AppShapes.Medium,
                        colors = if (polished) ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = titleColor,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = bodyColor,
                        ) else ButtonDefaults.buttonColors(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_wechat), null, Modifier.size(24.dp),
                                tint = if (polished) titleColor else palette.onPrimary)
                            Text(stringResource(if (authorizationStatus == WeChatAuthorizationStatus.Authorizing)
                                R.string.wechat_auth_authorizing else R.string.wechat_login_title), style = AppTextStyles.Button,
                                textAlign = TextAlign.Center)
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.Button)
                            .testTag("wechat_login_close")
                            .then(
                                if (polished) Modifier.vaultGradientAction(secondary = true, shape = AppShapes.Medium)
                                else Modifier,
                            ),
                        shape = AppShapes.Medium,
                        border = if (polished) null else BorderStroke(1.dp, palette.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = linkColor,
                        ),
                    ) {
                        Text(stringResource(R.string.wechat_login_skip), style = AppTextStyles.Button,
                            textAlign = TextAlign.Center)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val agreementDescription = stringResource(R.string.wechat_login_agreement_prefix) +
                        stringResource(R.string.wechat_login_user_agreement) +
                        stringResource(R.string.wechat_login_agreement_and) +
                        stringResource(R.string.wechat_login_privacy_policy)
                    Checkbox(
                        checked = state.agreementAccepted,
                        onCheckedChange = onAgreementChanged,
                        modifier = Modifier.testTag("wechat_login_agreement")
                            .semantics { contentDescription = agreementDescription },
                        colors = if (polished) CheckboxDefaults.colors(
                            checkedColor = VaultMainColors.Accent,
                            uncheckedColor = bodyColor,
                            checkmarkColor = VaultMainColors.Background,
                        ) else CheckboxDefaults.colors(),
                    )
                    val linkStyles = TextLinkStyles(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ))
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.wechat_login_agreement_prefix))
                            withLink(LinkAnnotation.Clickable("user_agreement", linkStyles) {
                                onOpenDocument(LegalDocumentType.UserAgreement)
                            }) { append(stringResource(R.string.wechat_login_user_agreement)) }
                            append(stringResource(R.string.wechat_login_agreement_and))
                            withLink(LinkAnnotation.Clickable("privacy_policy", linkStyles) {
                                onOpenDocument(LegalDocumentType.PrivacyPolicy)
                            }) { append(stringResource(R.string.wechat_login_privacy_policy)) }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = AppDimensions.Button),
                        style = AppTextStyles.BodySecondary.copy(lineBreak = LineBreak.Paragraph),
                        color = bodyColor,
                    )
                }
                if (state.agreementRequired) {
                    Text(
                        stringResource(R.string.wechat_login_agreement_required),
                        color = if (polished) AppColors.Error else palette.error,
                        style = AppTextStyles.Caption,
                        modifier = Modifier.testTag("wechat_login_agreement_error")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                wechatAuthorizationMessage(authorizationStatus)?.let { message ->
                    Text(
                        stringResource(message),
                        color = bodyColor,
                        style = AppTextStyles.Caption,
                        modifier = Modifier.testTag("wechat_login_placeholder")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {},
    )
}
