package com.aurora.calculatorvault.feature.legal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.legal.data.LegalDocumentRepository
import com.aurora.calculatorvault.feature.legal.data.LocalInformationRecords
import com.aurora.calculatorvault.feature.login.session.entitlementAccount
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultSecondaryBackdrop
import com.aurora.calculatorvault.ui.layout.appPagePadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CollectedInformationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as CalculatorVaultApp
    if (!app.privacyConsent.isAllowedNow()) return

    val trial by app.deviceTrial.state.collectAsState()
    val session by app.accountSession.state.collectAsState()
    val entitlement by app.entitlementRepository.state.collectAsState()
    val account = session.entitlementAccount
    val sdk = app.readSdkProcessingIndicators()
    var records by remember(app) { mutableStateOf<LocalInformationRecords?>(null) }
    var purchase by remember(account) { mutableStateOf(PendingPurchaseEvidence.Unknown) }
    val document by produceState<Result<String>?>(initialValue = null, key1 = app) {
        value = LegalDocumentRepository(app).readCollectedInformation()
    }

    LaunchedEffect(app) {
        records = readIfAvailable { app.readLocalInformationRecords() }
    }
    LaunchedEffect(app, account) {
        if (account != null && app.privacyConsent.isAllowedNow()) {
            val repository = app.paymentRepository
            val evidence = readIfAvailable {
                withContext(Dispatchers.IO) {
                    repository.pendingFor(account.userId).toCollectedPurchaseEvidence()
                }
            }
            if (app.privacyConsent.isAllowedNow() && app.accountSession.state.value.entitlementAccount == account) {
                purchase = evidence ?: PendingPurchaseEvidence.Unknown
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .vaultSecondaryBackdrop()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        VaultTopAppBar(
            title = stringResource(R.string.legal_collected_information_list_title),
            navigationIcon = {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(appPagePadding()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            InformationLine(stringResource(R.string.collected_intro))
            InformationSection(stringResource(R.string.collected_trial_title)) {
                InformationLine(
                    stringResource(R.string.collected_trial_status, stringResource(trial.status.collectedTrialStatus())),
                )
                InformationLine(
                    stringResource(
                        if (app.deviceTrial.hasVerifiedTrialResponse()) R.string.collected_trial_reply_recorded
                        else R.string.collected_trial_reply_unknown,
                    ),
                )
            }
            InformationSection(stringResource(R.string.collected_account_title)) {
                InformationLine(
                    stringResource(R.string.collected_account_status, stringResource(session.collectedAccountStatus())),
                )
                if (account != null) {
                    InformationLine(stringResource(R.string.collected_account_identifier_present))
                }
                InformationLine(
                    stringResource(
                        if (app.accountSession.hasLocalCredential()) R.string.collected_account_credential_present
                        else R.string.collected_account_credential_unverified,
                    ),
                )
            }
            InformationSection(stringResource(R.string.collected_membership_title)) {
                InformationLine(stringResource(session.collectedMembershipStatus(entitlement.source)))
                InformationLine(stringResource(purchase.description))
            }
            InformationSection(stringResource(R.string.collected_apps_title)) {
                RecordLine(stringResource(R.string.collected_hidden_apps), records?.hasHiddenApps)
                RecordLine(stringResource(R.string.collected_disguises), records?.hasDisguises)
                RecordLine(stringResource(R.string.collected_app_locks), records?.hasAppLocks)
            }
            InformationSection(stringResource(R.string.collected_media_title)) {
                RecordLine(stringResource(R.string.collected_media_records), records?.hasMedia)
            }
            InformationSection(stringResource(R.string.collected_sdk_title)) {
                InformationLine(
                    stringResource(
                        if (sdk.umengInitialized) R.string.collected_sdk_umeng_initialized
                        else R.string.collected_sdk_umeng_not_initialized,
                    ),
                )
                InformationLine(
                    stringResource(
                        if (sdk.buglyInitialized) R.string.collected_sdk_bugly_initialized
                        else R.string.collected_sdk_bugly_not_initialized,
                    ),
                )
                InformationLine(
                    stringResource(
                        if (sdk.wechatRegistered) R.string.collected_sdk_wechat_registered
                        else R.string.collected_sdk_wechat_not_registered,
                    ),
                )
                InformationLine(stringResource(R.string.collected_sdk_limits))
            }
            InformationSection(stringResource(R.string.collected_requests_title)) {
                InformationLine(stringResource(R.string.collected_requests, stringResource(R.string.contact_email)))
            }
            document?.let { result ->
                InformationSection(stringResource(R.string.collected_details_title)) {
                    InformationLine(result.getOrNull() ?: stringResource(R.string.legal_document_load_failed))
                }
            }
        }
    }
}

@Composable
private fun InformationSection(
    title: String,
    content: @Composable () -> Unit,
) {
    VaultCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
                text = title,
                style = AppTextStyles.CardTitle,
                color = if (useVaultDarkVisuals()) VaultMainColors.TextPrimary else MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun InformationLine(text: String) {
    Text(
        text = text,
        style = AppTextStyles.Body,
        color = if (useVaultDarkVisuals()) VaultMainColors.TextSecondary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun RecordLine(label: String, present: Boolean?) {
    InformationLine(stringResource(R.string.collected_local_row, label, stringResource(localRecordStatus(present))))
}

private suspend fun <T> readIfAvailable(read: suspend () -> T): T? = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
