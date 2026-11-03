package com.aurora.calculatorvault.feature.privatemedia.domain

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.ActionPolicy
import com.aurora.calculatorvault.feature.entitlement.domain.DenialReason
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I3.3.4 private-album import entitlement gates (JVM).
 * Does not hit Room, storage, payment, or trial stores.
 */
class PrivateAlbumImportEntitlementTest {

    private fun policy(allowed: AtomicBoolean) = ActionPolicy { capability ->
        require(capability == EntitlementCapability.PRIVATE_ALBUM_IMPORT)
        if (allowed.get()) ActionDecision.Allowed
        else ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
    }

    @Test
    fun `valid entitlement allows import gate`() {
        assertTrue(PrivateAlbumImportAccess.allowsImport(policy(AtomicBoolean(true))))
    }

    @Test
    fun `fake vip style allow policy allows import`() {
        // Mirrors Fake VIP ActionPolicy Allowed without production Fake hooks.
        val policy = ActionPolicy { ActionDecision.Allowed }
        assertTrue(PrivateAlbumImportAccess.allowsImport(policy))
    }

    @Test
    fun `no entitlement denies import gate`() {
        assertFalse(PrivateAlbumImportAccess.allowsImport(policy(AtomicBoolean(false))))
        assertEquals(
            ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT),
            PrivateAlbumImportAccess.evaluate(policy(AtomicBoolean(false))),
        )
    }

    @Test
    fun `picker return after expiry refuses entire batch without processing`() = runBlocking {
        val processed = AtomicInteger(0)
        val summary = PrivateAlbumImportBatch.run(
            itemCount = 5,
            importAllowed = { false },
        ) {
            processed.incrementAndGet()
            PrivateAlbumImportItemResult.Success(it.toLong())
        }
        assertEquals(0, summary.successCount)
        assertEquals(0, summary.failureCount)
        assertEquals(5, summary.skippedDueToEntitlement)
        assertEquals(0, processed.get())
    }

    @Test
    fun `batch stops before next item when entitlement drops mid-run and keeps successes`() = runBlocking {
        val allowedUntilExclusive = AtomicInteger(2)
        val processed = mutableListOf<Int>()
        val summary = PrivateAlbumImportBatch.run(
            itemCount = 5,
            importAllowed = { processed.size < allowedUntilExclusive.get() },
        ) { index ->
            processed += index
            PrivateAlbumImportItemResult.Success(100L + index)
        }
        assertEquals(listOf(0, 1), processed)
        assertEquals(2, summary.successCount)
        assertEquals(0, summary.failureCount)
        assertEquals(3, summary.skippedDueToEntitlement)
        assertEquals(listOf(100L, 101L), summary.importedMediaIds)
    }

    @Test
    fun `current item failure rolls back only that item and does not wipe prior successes`() = runBlocking {
        val summary = PrivateAlbumImportBatch.run(
            itemCount = 3,
            importAllowed = { true },
        ) { index ->
            when (index) {
                0 -> PrivateAlbumImportItemResult.Success(1L)
                1 -> PrivateAlbumImportItemResult.Failure
                else -> PrivateAlbumImportItemResult.Success(3L)
            }
        }
        assertEquals(2, summary.successCount)
        assertEquals(1, summary.failureCount)
        assertEquals(0, summary.skippedDueToEntitlement)
        assertEquals(listOf(1L, 3L), summary.importedMediaIds)
    }

    @Test
    fun `manage existing capabilities stay allowed when import is denied`() {
        val policy = ActionPolicy { capability ->
            when (capability) {
                EntitlementCapability.PRIVATE_ALBUM_IMPORT ->
                    ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
                EntitlementCapability.PRIVATE_ALBUM_VIEW_RESTORE,
                EntitlementCapability.PRIVATE_ALBUM_MANAGE_EXISTING,
                -> ActionDecision.Allowed
                else -> ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
            }
        }
        assertFalse(PrivateAlbumImportAccess.allowsImport(policy))
        assertTrue(policy.evaluate(EntitlementCapability.PRIVATE_ALBUM_VIEW_RESTORE) is ActionDecision.Allowed)
        assertTrue(policy.evaluate(EntitlementCapability.PRIVATE_ALBUM_MANAGE_EXISTING) is ActionDecision.Allowed)
    }

    @Test
    fun `entitlement flip uses live ActionPolicy not a sticky ticker flag`() {
        val allowed = AtomicBoolean(true)
        val p = policy(allowed)
        assertTrue(PrivateAlbumImportAccess.allowsImport(p))
        allowed.set(false)
        // Immediate re-evaluate (no 30s UI ticker).
        assertFalse(PrivateAlbumImportAccess.allowsImport(p))
    }

    @Test
    fun `page rebuild style re-entry still blocked when denied`() {
        val p = policy(AtomicBoolean(false))
        // Simulates Activity recreate / async callback calling the gate again.
        repeat(3) {
            assertFalse(PrivateAlbumImportAccess.allowsImport(p))
        }
    }

    @Test
    fun `cleanupMissingMediaRecords semantics are file-based not entitlement-based`() = runBlocking {
        // Documented contract: entitlement deny must not imply missing files.
        // This test asserts the import batch does not invent cleanup side effects.
        val summary = PrivateAlbumImportBatch.run(
            itemCount = 2,
            importAllowed = { false },
        ) { error("must not import") }
        assertEquals(0, summary.importedMediaIds.size)
        assertEquals(2, summary.skippedDueToEntitlement)
    }
}
