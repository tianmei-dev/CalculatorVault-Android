package com.aurora.calculatorvault.feature.trial

import android.content.Context
import android.provider.Settings
import android.os.SystemClock
import com.aurora.calculatorvault.feature.login.session.KeystoreTokenStore
import com.aurora.calculatorvault.feature.login.session.SessionCredential
import org.json.JSONObject
import java.io.File
import java.time.Instant

class AndroidDeviceIdentityProvider(private val context: Context, private val consent: com.aurora.calculatorvault.core.privacy.PrivacyConsentGate) : DeviceIdentityProvider {
    override fun deviceKey(): String? = consent.runIfAllowed { try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{16}")) && it != "0000000000000000" && it != "9774d56d682e549c" }
    } catch (_: Exception) { null } }
}
class AndroidTrialClock(private val context: Context) : TrialClock {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun boot(): String? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toString()
    } catch (_: Exception) { null }
}
/** Reuses the Keystore cipher with an isolated namespace; account logout cannot erase trial state. */
class EncryptedTrialStore(context: Context) : TrialStore {
    private val storage = KeystoreTokenStore(context, "device-trial-v1")
    private val marker = File(context.noBackupFilesDir, "device-trial-v1.seen")
    override fun seen() = marker.exists()
    override fun markSeen() { if (!marker.exists()) { check(marker.createNewFile()) } }
    override fun read(): TrialRecord? {
        val data = storage.read() ?: return null
        val j = JSONObject(data.token)
        return TrialRecord(j.getLong("first"), j.getLong("remaining"), j.getLong("wall"), j.getLong("elapsed"),
            j.optString("boot").takeIf { it.isNotEmpty() }, j.getBoolean("expired"),
            if (j.has("serverTime")) j.getLong("serverTime") else null,
            if (j.has("serverStarted")) j.getLong("serverStarted") else null,
            if (j.has("serverExpires")) j.getLong("serverExpires") else null,
            j.getBoolean("verified"), j.getBoolean("uncertain"))
    }
    override fun write(record: TrialRecord) {
        val j = JSONObject().put("first", record.firstOpened).put("remaining", record.remaining)
            .put("wall", record.wall).put("elapsed", record.elapsed).put("boot", record.boot)
            .put("expired", record.expired).put("serverTime", record.serverTime)
            .put("serverStarted", record.serverStarted).put("serverExpires", record.serverExpires)
            .put("verified", record.verified).put("uncertain", record.uncertain)
        storage.write(SessionCredential(j.toString(), Instant.EPOCH))
    }
}
