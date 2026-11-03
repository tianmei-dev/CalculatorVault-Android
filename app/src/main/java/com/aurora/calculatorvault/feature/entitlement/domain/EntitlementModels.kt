package com.aurora.calculatorvault.feature.entitlement.domain

import com.aurora.calculatorvault.feature.trial.TrialStatus

/** Paid or free feature buckets evaluated by [ActionPolicy]. */
enum class EntitlementCapability {
    /** App management is always free. PIN / privacy remain separate gates. */
    APP_MANAGEMENT,
    /** Actual app-lock monitoring / overlay protection (I3.3.3 will enforce). */
    APP_LOCK_PROTECTION,
    /** Browse / preview / restore existing private media. */
    PRIVATE_ALBUM_VIEW_RESTORE,
    /** Import new media from the system gallery. */
    PRIVATE_ALBUM_IMPORT,
    /** Rename / move / delete existing media and empty folders. */
    PRIVATE_ALBUM_MANAGE_EXISTING,
}

/** Which source currently authorizes paid capabilities. */
sealed interface EntitlementSource {
    data object None : EntitlementSource
    data class DeviceTrial(
        val status: TrialStatus,
        val remainingMillis: Long,
    ) : EntitlementSource {
        init {
            require(remainingMillis > 0)
        }
    }
    data class Vip(
        val accountId: Long,
        /** Wall-clock epoch millis; null means permanent. */
        val expiresAtEpochMillis: Long?,
        val permanent: Boolean = expiresAtEpochMillis == null,
    ) : EntitlementSource
}

/**
 * Immutable snapshot for UI and diagnostics.
 * [ActionPolicy] must re-evaluate with a trusted clock; do not treat this as a lease past [trialDeadlineElapsedMillis].
 */
data class EntitlementState(
    val source: EntitlementSource = EntitlementSource.None,
    val trialStatus: TrialStatus = TrialStatus.NOT_INITIALIZED,
    val trialRemainingMillis: Long = 0L,
    /** ElapsedRealtime deadline while a trial grant is active; null if trial does not grant. */
    val trialDeadlineElapsedMillis: Long? = null,
    val accountId: Long? = null,
    val accountGeneration: Long = 0L,
    val vipExpiresAtEpochMillis: Long? = null,
    val vipPermanent: Boolean = false,
    val evaluatedAtElapsedMillis: Long = 0L,
) {
    val hasPaidEntitlement: Boolean
        get() = source !is EntitlementSource.None
}

enum class DenialReason {
    REQUIRES_ENTITLEMENT,
    TRIAL_EXPIRED,
    TRIAL_UNTRUSTWORTHY,
    VIP_INACTIVE,
}

sealed interface ActionDecision {
    data object Allowed : ActionDecision
    data class Denied(val reason: DenialReason) : ActionDecision
}

/** Whether [TrialStatus] may grant trial capabilities when remaining time is still trusted and > 0. */
fun TrialStatus.grantsTrialCapability(): Boolean = when (this) {
    TrialStatus.ACTIVE,
    TrialStatus.PENDING_SYNC,
    TrialStatus.TEMPORARILY_UNAVAILABLE,
    -> true
    TrialStatus.VERIFICATION_REQUIRED,
    TrialStatus.EXPIRED,
    TrialStatus.NOT_INITIALIZED,
    -> false
}
