package com.aurora.calculatorvault.feature.login.domain

import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WeChatAuthorizationStatus {
    Idle, Authorizing, AppIdMissing, WeChatUnavailable, InitializationFailed,
    LaunchFailed, Cancelled, Failed, InvalidCallback, Expired, StorageFailed,
    CodeReceived, HandoffFailed, PrivacyRequired,
}

enum class WeChatSdkAvailability { Ready, AppIdMissing, WeChatUnavailable, InitializationFailed }

interface WeChatSdkBridge {
    fun prepare(): WeChatSdkAvailability
    fun sendAuthorization(scope: String, state: String): Boolean
    fun release()
}

/** Only this nonce and timestamp may survive process death; never an authorization code. */
class PendingWeChatAuthorization(val state: String, val createdAtMillis: Long) {
    override fun toString() = "PendingWeChatAuthorization([REDACTED])"
}

interface PendingWeChatAuthorizationStore {
    fun read(): PendingWeChatAuthorization?
    fun save(pending: PendingWeChatAuthorization): Boolean
    fun clear(): Boolean
}

/** App-scoped, serialized coordinator. Neither results nor flows contain a code or login session. */
class WeChatAuthorizationCoordinator(
    private val sdk: WeChatSdkBridge,
    private val store: PendingWeChatAuthorizationStore,
    private val serverLogin: WeChatServerLoginEntry,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newState: () -> String = ::secureWeChatState,
    private val consentAllowed: () -> Boolean = { true },
) {
    private val mutableStatus = MutableStateFlow(WeChatAuthorizationStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private var cancelled = false

    @Synchronized
    fun start() {
        if (!consentAllowed()) { cancel(privacyWithdrawn = true); return }
        try {
            val pending = store.read()
            if (!cancelled && pending != null && isFresh(pending) &&
                mutableStatus.value != WeChatAuthorizationStatus.InvalidCallback &&
                mutableStatus.value != WeChatAuthorizationStatus.StorageFailed) {
                mutableStatus.value = WeChatAuthorizationStatus.Authorizing
                return
            }
            if (pending != null && !store.clear()) {
                mutableStatus.value = WeChatAuthorizationStatus.StorageFailed
                return
            }
            val availability = try { sdk.prepare() } catch (_: Exception) { WeChatSdkAvailability.InitializationFailed }
            when (availability) {
                WeChatSdkAvailability.AppIdMissing -> { mutableStatus.value = WeChatAuthorizationStatus.AppIdMissing; return }
                WeChatSdkAvailability.WeChatUnavailable -> { mutableStatus.value = WeChatAuthorizationStatus.WeChatUnavailable; return }
                WeChatSdkAvailability.InitializationFailed -> { mutableStatus.value = WeChatAuthorizationStatus.InitializationFailed; return }
                WeChatSdkAvailability.Ready -> Unit
            }
            val request = PendingWeChatAuthorization(newState(), clock())
            cancelled = true
            if (!store.save(request)) {
                mutableStatus.value = WeChatAuthorizationStatus.StorageFailed
                return
            }
            cancelled = false
            mutableStatus.value = WeChatAuthorizationStatus.Authorizing
            val sent = try { sdk.sendAuthorization("snsapi_userinfo", request.state) } catch (_: Exception) { false }
            if (!sent) {
                cancelled = true
                clearWithStatus(WeChatAuthorizationStatus.LaunchFailed)
            }
        } catch (_: Exception) {
            cancelled = true
            clearWithStatus(WeChatAuthorizationStatus.StorageFailed)
        }
    }

    /** Also recovers a process-restored request, or expires a request with no SDK callback. */
    @Synchronized
    fun refresh() {
        if (cancelled) return
        try {
            val pending = store.read()
            if (pending != null) {
                if (isFresh(pending)) {
                    if (mutableStatus.value == WeChatAuthorizationStatus.Idle) {
                        mutableStatus.value = WeChatAuthorizationStatus.Authorizing
                    }
                } else clearWithStatus(WeChatAuthorizationStatus.Expired)
            }
        } catch (_: Exception) {
            mutableStatus.value = WeChatAuthorizationStatus.StorageFailed
        }
    }

    @Synchronized
    fun hasPendingRequest(): Boolean = try { store.read() != null } catch (_: Exception) { false }

    /** errorCode: official BaseResp codes (0 success, -2 cancel; all others failure). */
    @Synchronized
    fun onAuthorizationResponse(errorCode: Int, callbackState: String?, code: String?) {
        if (cancelled) return
        if (!consentAllowed()) { cancel(privacyWithdrawn = true); return }
        try {
            val pending = store.read()
            // Replays after consumption must not overwrite the result or trigger another handoff.
            if (pending == null) {
                if (mutableStatus.value == WeChatAuthorizationStatus.Idle) {
                    mutableStatus.value = WeChatAuthorizationStatus.InvalidCallback
                }
                return
            }
            if (!isFresh(pending)) { clearWithStatus(WeChatAuthorizationStatus.Expired); return }
            if (callbackState == null || !MessageDigest.isEqual(
                    pending.state.toByteArray(Charsets.UTF_8), callbackState.toByteArray(Charsets.UTF_8))) {
                mutableStatus.value = WeChatAuthorizationStatus.InvalidCallback
                return // An unrelated callback cannot consume a valid pending request.
            }
            // Durably consume before touching the code or handing off to Server.
            if (!store.clear()) { mutableStatus.value = WeChatAuthorizationStatus.StorageFailed; return }
            mutableStatus.value = when {
                errorCode == -2 -> WeChatAuthorizationStatus.Cancelled
                errorCode != 0 || code.isNullOrBlank() -> WeChatAuthorizationStatus.Failed
                else -> {
                    try {
                        serverLogin.acceptAuthorization(WeChatServerLoginRequest(code))
                        WeChatAuthorizationStatus.CodeReceived
                    } catch (_: Exception) { WeChatAuthorizationStatus.HandoffFailed }
                }
            }
        } catch (_: Exception) { mutableStatus.value = WeChatAuthorizationStatus.StorageFailed }
    }

    @Synchronized
    fun rejectIntent() {
        mutableStatus.value = WeChatAuthorizationStatus.InvalidCallback
    }

    @Synchronized
    fun cancel(privacyWithdrawn: Boolean = false) {
        cancelled = true
        clearWithStatus(if (privacyWithdrawn) WeChatAuthorizationStatus.PrivacyRequired else WeChatAuthorizationStatus.Idle)
        if (privacyWithdrawn) sdk.release()
    }

    private fun isFresh(pending: PendingWeChatAuthorization): Boolean =
        clock() - pending.createdAtMillis in 0 until REQUEST_TTL_MILLIS

    private fun clearWithStatus(result: WeChatAuthorizationStatus) {
        mutableStatus.value = try {
            if (store.clear()) result else WeChatAuthorizationStatus.StorageFailed
        } catch (_: Exception) { WeChatAuthorizationStatus.StorageFailed }
    }

    companion object { const val REQUEST_TTL_MILLIS = 5 * 60 * 1000L }
}

internal fun secureWeChatState(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
