package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisguiseShortcutIconFactoryTest {
    @Test
    fun everyBuiltInIconCreatesAnIconCompat() {
        val factory = ResourceDisguiseShortcutIconFactory(
            ApplicationProvider.getApplicationContext<Context>(),
        )

        DisguiseIconId.entries.forEach { iconId ->
            assertNotNull(factory.create(iconId))
        }
    }
}
