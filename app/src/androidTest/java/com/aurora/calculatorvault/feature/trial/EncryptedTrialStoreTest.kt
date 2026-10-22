package com.aurora.calculatorvault.feature.trial

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import com.aurora.calculatorvault.feature.login.session.KeystoreTokenStore

class EncryptedTrialStoreTest {
    @Test fun accountLogoutPreservesTrialAndCiphertextIsNotPlaintext() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val store=EncryptedTrialStore(context)
        val original=store.read()
        val value=TrialRecord(1800000000000L,123456L,1800000000000L,5000,"test-boot")
        try {
            store.markSeen(); store.write(value)
            KeystoreTokenStore(context).clear()
            assertEquals(value,EncryptedTrialStore(context).read())
            val text=java.io.File(context.noBackupFilesDir,"device-trial-v1.enc").readBytes().toString(Charsets.UTF_8)
            assertFalse(text.contains("test-boot")); assertFalse(text.contains("123456"))
        } finally { if (original != null) store.write(original) }
    }
}
