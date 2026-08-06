package com.aurora.calculatorvault.feature.disguise.shortcut

/** 快捷入口授权仅存在于当前 Activity/ViewModel 生命周期内。 */
class DisguiseShortcutSession {
    private var shortcutId: String? = null
    private var verificationInProgress = false
    private var launchInProgress = false

    @Synchronized
    fun begin(id: String) {
        clear()
        shortcutId = id
    }

    @Synchronized
    fun currentShortcutId(): String? = shortcutId

    @Synchronized
    fun tryStartVerification(): Boolean {
        if (shortcutId == null || verificationInProgress || launchInProgress) return false
        verificationInProgress = true
        return true
    }

    @Synchronized
    fun finishVerification() {
        verificationInProgress = false
    }

    @Synchronized
    fun tryStartLaunch(): Boolean {
        if (shortcutId == null || launchInProgress) return false
        verificationInProgress = false
        launchInProgress = true
        return true
    }

    @Synchronized
    fun clear() {
        shortcutId = null
        verificationInProgress = false
        launchInProgress = false
    }
}
