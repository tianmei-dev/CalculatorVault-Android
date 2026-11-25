package com.aurora.calculatorvault.feature.legal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.legal.data.LegalDocumentRepository
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalDocumentRepositoryTest {
    @Test
    fun settingsAndPurchaseDocumentsResolveToPackagedAssets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LegalDocumentRepository(context)
        val documents = LegalDocumentType.entries.associateWith { type ->
            val route = VaultTabRoute.LegalDocument.createRoute(type)
            assertEquals(type, LegalDocumentType.fromRouteValue(route.substringAfterLast('/')))
            repository.read(type).getOrThrow().also { body ->
                assertTrue("${type.assetPath} is blank", body.isNotBlank())
            }
        }

        val membership = documents.getValue(LegalDocumentType.MembershipServiceAgreement)
        assertTrue(membership.startsWith("应用加密计算器会员服务协议"))
        assertTrue("待发布审核稿" in membership)

        val sharing = documents.getValue(LegalDocumentType.ThirdPartyInformationSharingList)
        assertTrue(sharing.startsWith("应用加密计算器与第三方共享个人信息清单"))
        assertTrue("待发布审核稿" in sharing)
        assertNotEquals(documents.getValue(LegalDocumentType.ThirdPartySdkList), sharing)
    }
}
