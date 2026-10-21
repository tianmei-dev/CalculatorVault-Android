package com.aurora.calculatorvault.feature.settings

import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.settings.presentation.AccountPreviewScenes
import com.aurora.calculatorvault.feature.settings.presentation.AccountStatus
import com.aurora.calculatorvault.feature.settings.presentation.AccountUiState
import com.aurora.calculatorvault.feature.settings.presentation.MembershipStatus
import com.aurora.calculatorvault.feature.settings.presentation.membershipText
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Debug tests share the debug-only preview scenarios; release tests never reference fixtures. */
class AccountUiStateTest {
    @Test fun `production default creates neither trial nor VIP`() {
        val state = AccountUiState()
        assertEquals(AccountStatus.Guest, state.account)
        assertEquals(MembershipStatus.NotSubscribed, state.visibleMembership)
        assertNull(state.visibleMembership.trialRemaining)
        assertEquals(R.string.account_vip_inactive, membershipText(state).title)
    }

    @Test fun `six debug scenarios map to required headings`() {
        assertEquals(6, AccountPreviewScenes.scenes.size)
        assertEquals(listOf(R.string.account_trial_active, R.string.account_vip_inactive,
            R.string.account_open_vip, R.string.vip_title, R.string.vip_title, R.string.account_vip_permanent),
            AccountPreviewScenes.scenes.map { membershipText(it.state).title })
    }

    @Test fun `trial snapshot displays whole days and remaining hours without a clock`() {
        val state = AccountPreviewScenes.scenes.first().state
        assertEquals(listOf(2L, 5L), membershipText(state).arguments)
        assertEquals(listOf(0L, 0L), membershipText(state.copy(
            deviceTrial = MembershipStatus.TrialActive(Duration.ofMinutes(59)))).arguments)
    }

    @Test fun `month and year retain plan and ISO expiry date`() {
        val monthly = AccountPreviewScenes.scenes[3].state
        val annual = AccountPreviewScenes.scenes[4].state
        assertEquals(VipPlan.Monthly, monthly.visibleMembership.planType)
        assertEquals(VipPlan.Annual, annual.visibleMembership.planType)
        assertEquals(listOf("2026-11-20"), membershipText(monthly).arguments)
        assertEquals(listOf("2027-10-21"), membershipText(annual).arguments)
        assertEquals(R.string.account_vip_expires, membershipText(annual).subtitle)
    }

    @Test fun `permanent membership has no synthetic expiration`() {
        val state = AccountPreviewScenes.scenes.last().state
        assertEquals(VipPlan.Lifetime, state.visibleMembership.planType)
        assertNull(state.visibleMembership.expiresAt)
        assertEquals(R.string.vip_duration_lifetime, membershipText(state).subtitle)
    }

    @Test fun `login alone does not imply membership`() {
        val state = AccountUiState(account = AccountStatus.WeChatLoggedIn())
        assertEquals(MembershipStatus.NotSubscribed, state.visibleMembership)
        assertEquals(R.string.account_open_vip_description, membershipText(state).subtitle)
    }

    @Test fun `guest hides account membership and preserves independent trial`() {
        val trial = MembershipStatus.TrialActive(Duration.ofHours(25))
        val loggedIn = AccountUiState(AccountStatus.WeChatLoggedIn("测试用户"), MembershipStatus.VipPermanent, trial)
        val guest = loggedIn.copy(account = AccountStatus.Guest)
        assertEquals(MembershipStatus.VipPermanent, loggedIn.visibleMembership)
        assertEquals(trial, guest.deviceTrial)
        assertEquals(trial, guest.visibleMembership)
        assertEquals(listOf(1L, 1L), membershipText(guest).arguments)
    }

    @Test fun `guest cannot display stale VIP after trial expires`() {
        val guest = AccountUiState(accountMembership = MembershipStatus.VipPermanent,
            deviceTrial = MembershipStatus.TrialExpired)
        assertEquals(R.string.account_vip_inactive, membershipText(guest).title)
    }

    @Test fun `missing or blank nicknames use fallback while supplied names are trimmed`() {
        assertNull(AccountStatus.WeChatLoggedIn().displayNickname)
        assertNull(AccountStatus.WeChatLoggedIn("  ").displayNickname)
        assertEquals("微信昵称", AccountStatus.WeChatLoggedIn(" 微信昵称 ").displayNickname)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `device trial cannot contain account VIP`() {
        AccountUiState(deviceTrial = MembershipStatus.VipPermanent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `trial snapshot cannot have negative remaining time`() {
        MembershipStatus.TrialActive(Duration.ofHours(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `permanent plan cannot be modeled as time limited VIP`() {
        MembershipStatus.VipActive(VipPlan.Lifetime, LocalDate.of(2027, 1, 1))
    }
}
