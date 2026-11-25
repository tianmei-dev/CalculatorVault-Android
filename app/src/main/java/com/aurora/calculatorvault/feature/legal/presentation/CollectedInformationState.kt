package com.aurora.calculatorvault.feature.legal.presentation

import androidx.annotation.StringRes
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.entitlementAccount
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.payment.data.PendingPurchaseRecord
import com.aurora.calculatorvault.feature.trial.TrialStatus

@StringRes
internal fun TrialStatus.collectedTrialStatus(): Int = when (this) {
    TrialStatus.NOT_INITIALIZED -> R.string.collected_trial_not_initialized
    TrialStatus.ACTIVE -> R.string.collected_trial_active
    TrialStatus.EXPIRED -> R.string.collected_trial_expired
    TrialStatus.PENDING_SYNC -> R.string.collected_trial_pending
    TrialStatus.TEMPORARILY_UNAVAILABLE -> R.string.collected_trial_unavailable
    TrialStatus.VERIFICATION_REQUIRED -> R.string.collected_trial_verification_required
}

@StringRes
internal fun AccountSession.collectedAccountStatus(): Int = when (this) {
    AccountSession.Guest -> R.string.collected_account_guest
    AccountSession.Authenticating -> R.string.collected_account_authenticating
    is AccountSession.Authenticated -> R.string.collected_account_authenticated
    is AccountSession.Validating -> R.string.collected_account_validating
    is AccountSession.TemporarilyUnavailable -> R.string.collected_account_unavailable
    AccountSession.SessionExpired -> R.string.collected_account_expired
    AccountSession.LoginFailed -> R.string.collected_account_login_failed
    AccountSession.StorageFailed -> R.string.collected_account_storage_failed
}

@StringRes
internal fun AccountSession.collectedMembershipStatus(source: EntitlementSource): Int {
    val account = entitlementAccount ?: return R.string.collected_membership_unknown_account
    val vip = source as? EntitlementSource.Vip
    if (vip == null || vip.accountId != account.userId) {
        return R.string.collected_membership_no_confirmed_vip
    }
    if (vip.permanent) return R.string.collected_membership_lifetime
    return when (VipPlan.fromServerCode(vip.planType)) {
        VipPlan.Monthly -> R.string.collected_membership_monthly
        VipPlan.Annual -> R.string.collected_membership_annual
        else -> R.string.collected_membership_other
    }
}

internal enum class PendingPurchaseEvidence(@StringRes val description: Int) {
    Unknown(R.string.collected_purchase_unverified),
    NoLocalRecord(R.string.collected_purchase_no_local_record),
    RequestRecorded(R.string.collected_purchase_request_recorded),
    OrderReferenceRecorded(R.string.collected_purchase_order_reference),
}

internal fun PendingPurchaseRecord?.toCollectedPurchaseEvidence(): PendingPurchaseEvidence = when {
    this == null -> PendingPurchaseEvidence.NoLocalRecord
    orderNo == null -> PendingPurchaseEvidence.RequestRecorded
    else -> PendingPurchaseEvidence.OrderReferenceRecorded
}

@StringRes
internal fun localRecordStatus(present: Boolean?): Int = when (present) {
    true -> R.string.collected_local_present
    false -> R.string.collected_local_absent
    null -> R.string.collected_local_unknown
}
