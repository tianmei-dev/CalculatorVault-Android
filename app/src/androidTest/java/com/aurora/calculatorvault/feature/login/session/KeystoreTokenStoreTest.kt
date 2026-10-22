package com.aurora.calculatorvault.feature.login.session

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Uses only test credentials; requires a device with Android Keystore. */
class KeystoreTokenStoreTest {
    @Test fun encryptedColdReadLogoutAndTamper() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KeystoreTokenStore(context)
        store.clear()
        try {
            val token = "instrumentation-test-only-token"
            store.write(SessionCredential(token, Instant.parse("2099-01-01T00:00:00Z")))
            val file = File(context.noBackupFilesDir, "account-session.enc")
            assertFalse(String(file.readBytes(), Charsets.UTF_8).contains(token))
            assertEquals(token, KeystoreTokenStore(context).read()?.token)
            val corrupted = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            file.writeBytes(corrupted)
            try { store.read(); fail("Corrupt ciphertext must be rejected") } catch (_: java.security.GeneralSecurityException) { }
            store.clear()
            assertNull(KeystoreTokenStore(context).read())
        } finally { store.clear() }
    }
}
