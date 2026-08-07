package com.aurora.calculatorvault.feature.applock.domain

class AppLockSessionManager {
    private var currentlyVerifyingPackage: String? = null
    private var temporarilyUnlockedPackage: String? = null
    private var lastForegroundPackage: String? = null
    private var lastForegroundChangedAt: Long = 0L

    @Synchronized
    fun beginVerification(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (currentlyVerifyingPackage == packageName) return false
        if (temporarilyUnlockedPackage == packageName) return false
        currentlyVerifyingPackage = packageName
        return true
    }

    @Synchronized
    fun updateForegroundPackage(packageName: String, changedAt: Long): Boolean {
        if (lastForegroundPackage == packageName) return false
        lastForegroundPackage = packageName
        lastForegroundChangedAt = changedAt
        return true
    }

    @Synchronized
    fun markUnlocked(packageName: String) {
        if (packageName.isBlank()) return
        temporarilyUnlockedPackage = packageName
        currentlyVerifyingPackage = null
    }

    @Synchronized
    fun isTemporarilyUnlocked(packageName: String): Boolean =
        temporarilyUnlockedPackage == packageName

    @Synchronized
    fun clearUnlocked(packageName: String) {
        if (temporarilyUnlockedPackage == packageName) {
            temporarilyUnlockedPackage = null
        }
    }

    @Synchronized
    fun finishVerification(packageName: String) {
        if (currentlyVerifyingPackage == packageName) {
            currentlyVerifyingPackage = null
        }
    }

    @Synchronized
    fun currentUnlockedPackage(): String? = temporarilyUnlockedPackage

    @Synchronized
    fun currentVerifyingPackage(): String? = currentlyVerifyingPackage

    @Synchronized
    fun lastForegroundPackage(): String? = lastForegroundPackage

    @Synchronized
    fun lastForegroundChangedAt(): Long = lastForegroundChangedAt

    @Synchronized
    fun clearAll() {
        currentlyVerifyingPackage = null
        temporarilyUnlockedPackage = null
        lastForegroundPackage = null
        lastForegroundChangedAt = 0L
    }
}
