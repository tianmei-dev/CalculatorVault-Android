package com.aurora.calculatorvault.feature.legal.presentation

import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.payment.data.PendingPurchaseRecord
import com.aurora.calculatorvault.feature.trial.TrialStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectedInformationStateTest {
    @Test
    fun trialStatusSeparatesActiveExpiredAndNotVerified() {
        assertEquals(R.string.collected_trial_active, TrialStatus.ACTIVE.collectedTrialStatus())
        assertEquals(R.string.collected_trial_expired, TrialStatus.EXPIRED.collectedTrialStatus())
        assertEquals(
            R.string.collected_trial_verification_required,
            TrialStatus.VERIFICATION_REQUIRED.collectedTrialStatus(),
        )
    }

    @Test
    fun guestAndVerifiedWechatLoginHaveDifferentStatuses() {
        assertEquals(R.string.collected_account_guest, AccountSession.Guest.collectedAccountStatus())
        assertEquals(
            R.string.collected_account_authenticated,
            AccountSession.Authenticated(ServerAccount(1)).collectedAccountStatus(),
        )
        assertEquals(R.string.collected_account_validating, AccountSession.Validating().collectedAccountStatus())
    }

    @Test
    fun membershipIsShownOnlyForTheMatchingTrustedAccount() {
        val active = EntitlementSource.Vip(accountId = 1, expiresAtEpochMillis = 1_000, planType = "YEARLY")
        assertEquals(
            R.string.collected_membership_unknown_account,
            AccountSession.Guest.collectedMembershipStatus(active),
        )
        assertEquals(
            R.string.collected_membership_no_confirmed_vip,
            AccountSession.Authenticated(ServerAccount(2)).collectedMembershipStatus(active),
        )
        assertEquals(
            R.string.collected_membership_annual,
            AccountSession.Authenticated(ServerAccount(1)).collectedMembershipStatus(active),
        )
    }

    @Test
    fun membershipWithoutActiveEntitlementDoesNotClaimHistoricalExpiryOrPurchase() {
        val session = AccountSession.Authenticated(ServerAccount(1))
        assertEquals(
            R.string.collected_membership_no_confirmed_vip,
            session.collectedMembershipStatus(EntitlementSource.None),
        )
        assertEquals(
            R.string.collected_membership_lifetime,
            session.collectedMembershipStatus(EntitlementSource.Vip(1, null)),
        )
    }

    @Test
    fun pendingPurchaseShowsOnlyEvidenceCategory() {
        val pending = PendingPurchaseRecord(1, "MONTHLY", "test-request", null)
        assertEquals(PendingPurchaseEvidence.NoLocalRecord, null.toCollectedPurchaseEvidence())
        assertEquals(PendingPurchaseEvidence.RequestRecorded, pending.toCollectedPurchaseEvidence())
        assertEquals(
            PendingPurchaseEvidence.OrderReferenceRecorded,
            pending.copy(orderNo = "test-order").toCollectedPurchaseEvidence(),
        )
    }

    @Test
    fun recordPresenceDistinguishesMissingDataFromAnUnavailableRead() {
        assertEquals(R.string.collected_local_present, localRecordStatus(true))
        assertEquals(R.string.collected_local_absent, localRecordStatus(false))
        assertEquals(R.string.collected_local_unknown, localRecordStatus(null))
    }
}
