package com.aurora.calculatorvault.feature.privatemedia

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import com.aurora.calculatorvault.feature.entitlement.InstrumentationEntitlementOverride
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingResult
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaType
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaEffect
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaViewModel
import com.aurora.calculatorvault.feature.privatemedia.storage.VaultMediaStorage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I3.3.4-B private-album entitlement acceptance on CalculatorVault_Test.
 *
 * Fake entitlement: androidTest [EntitlementInstrumentationBridge] only.
 * Media: synthetic MediaStore JPEG/MP4 with [SyntheticMediaFactory.TEST_PREFIX].
 *
 * Run via `adb install -r` + `am instrument` (not connectedDebugAndroidTest).
 *
 * Verification levels:
 * - COMMAND: ViewModel/Repository + real ContentResolver/files/Room
 * - UI gate: effects OpenMediaPicker / NavigateToVip (Picker UI itself not automated)
 */
@RunWith(AndroidJUnit4::class)
class PrivateMediaEntitlementInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app: CalculatorVaultApp = context.applicationContext as CalculatorVaultApp
    private val storage = VaultMediaStorage(context)

    private val createdMediaIds = CopyOnWriteArrayList<Long>()
    private val createdAlbumIds = CopyOnWriteArrayList<Long>()
    private val syntheticUris = CopyOnWriteArrayList<Uri>()
    private var baselineMediaIds: Set<Long> = emptySet()
    private var baselineAlbumIds: Set<Long> = emptySet()

    @Before
    fun setUp() = runBlocking {
        assertEmulatorIdentity()
        ensureInitialized()
        EntitlementInstrumentationBridge.clear(app)
        baselineMediaIds = snapshotMediaIds()
        baselineAlbumIds = snapshotAlbumIds()
        logEvidence("baseline", "media=${baselineMediaIds.size} albums=${baselineAlbumIds.size}")
    }

    @After
    fun tearDown() = runBlocking {
        EntitlementInstrumentationBridge.clear(app)
        // Cleanup only this test's media/albums; never wipe baseline.
        if (createdMediaIds.isNotEmpty()) {
            app.vaultMediaRepository.deletePrivateMedia(createdMediaIds.toList())
        }
        createdAlbumIds.forEach { id ->
            runCatching { app.vaultMediaRepository.deleteAlbum(id) }
        }
        syntheticUris.forEach { SyntheticMediaFactory.deleteUri(context, it) }
        createdMediaIds.clear()
        createdAlbumIds.clear()
        syntheticUris.clear()
        val afterMedia = snapshotMediaIds()
        assertTrue(
            "Baseline media must remain after cleanup",
            baselineMediaIds.all { it in afterMedia },
        )
    }

    @Test
    fun normalImport_realFilesAndRoom() = runBlocking {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
        assertTrue(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        assertTrue(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
                is ActionDecision.Allowed,
        )

        val image = trackUri(SyntheticMediaFactory.createJpegUri(context, 1))
        val video = trackUri(SyntheticMediaFactory.createVideoUri(context, 1))
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val summary = app.vaultMediaRepository.importMedia(album.id, listOf(image, video))
        trackImported(summary.importedMediaIds)

        assertEquals(2, summary.successCount)
        assertEquals(0, summary.failureCount)
        assertEquals(0, summary.skippedDueToEntitlement)

        val items = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .filter { it.media.id in createdMediaIds }
        assertEquals(2, items.size)
        assertTrue(items.any { it.media.mediaType == VaultMediaType.IMAGE })
        assertTrue(items.any { it.media.mediaType == VaultMediaType.VIDEO })
        items.forEach { item ->
            assertTrue("encrypted file missing ${item.media.privateFileName}", item.file.exists())
            assertTrue(item.file.length() > 0L)
            assertEquals(item.file, storage.privateFile(item.media.privateFileName))
        }
        logEvidence(
            "normal_import",
            "ids=${summary.importedMediaIds} files=${items.map { "${it.media.privateFileName}:${it.file.length()}" }}",
        )
    }

    @Test
    fun forceNone_blocksPickerAndCreatesNoMedia() = runBlocking {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceNone)
        assertFalse(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
                is ActionDecision.Allowed,
        )
        val beforeFiles = listVaultFiles()
        val beforeIds = snapshotMediaIds()
        val album = app.vaultMediaRepository.ensureDefaultAlbum()

        val effects = collectEffects { vm ->
            vm.requestImportToAlbum(album.id)
            SystemClock.sleep(300)
        }
        assertTrue("must navigate VIP", effects.any { it is PrivateMediaEffect.NavigateToVip })
        assertFalse("must not open picker", effects.any { it is PrivateMediaEffect.OpenMediaPicker })

        assertEquals(beforeIds, snapshotMediaIds())
        assertEquals(beforeFiles, listVaultFiles())
        logEvidence("force_none_block", "effects=$effects filesUnchanged=true")
    }

    @Test
    fun pickerReturnAfterRevoke_doesNotImport() = runBlocking {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val uri = trackUri(SyntheticMediaFactory.createJpegUri(context, 2))
        val beforeIds = snapshotMediaIds()
        val beforeFiles = listVaultFiles()

        // Simulate: picker opened while VIP; revoke before import command.
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceNone)
        val effects = collectEffects { vm ->
            vm.importMedia(album.id, listOf(uri))
            SystemClock.sleep(400)
        }
        assertTrue(effects.any { it is PrivateMediaEffect.NavigateToVip })
        assertFalse(effects.any { it is PrivateMediaEffect.ImportCompleted })
        assertEquals(beforeIds, snapshotMediaIds())
        assertEquals(beforeFiles, listVaultFiles())
        logEvidence("picker_revoke", "noImport=true")
    }

    @Test
    fun batchMidEntitlementLoss_keepsSuccessesStopsRest() = runBlocking {
        // COMMAND verification: single repository.importMedia with evaluation budget.
        // Budget=2 → first two per-item ActionPolicy evaluations allow, then ForceNone.
        val uris = (1..5).map { trackUri(SyntheticMediaFactory.createJpegUri(context, 10 + it)) }
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        // Arm budget immediately before import so refresh does not consume slots.
        EntitlementInstrumentationBridge.armImportEvaluationBudget(app, budget = 2)

        val summary = app.vaultMediaRepository.importMedia(album.id, uris)
        trackImported(summary.importedMediaIds)

        assertEquals(2, summary.successCount)
        assertEquals(0, summary.failureCount)
        assertEquals(3, summary.skippedDueToEntitlement)
        assertEquals(2, summary.importedMediaIds.size)

        val kept = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .filter { it.media.id in createdMediaIds }
        assertEquals(2, kept.size)
        kept.forEach { assertTrue(it.file.exists() && it.file.length() > 0L) }

        // Remaining synthetic URIs must not appear as new vault files beyond the 2 successes.
        val vaultFiles = listVaultFiles()
        assertEquals(2, kept.count { it.file.name in vaultFiles.map { f -> f.name } })
        logEvidence(
            "mid_batch",
            "success=${summary.successCount} skipped=${summary.skippedDueToEntitlement} files=${kept.map { it.file.name }}",
        )
    }

    @Test
    fun singleItemFailure_rollsBackOnlyThatItem() = runBlocking {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
        // Distinct URIs required — repository.importMedia collapses duplicates.
        val good1 = trackUri(SyntheticMediaFactory.createJpegUri(context, 20))
        val good2 = trackUri(SyntheticMediaFactory.createJpegUri(context, 21))
        val bad = Uri.parse("content://com.aurora.calculatorvault.test.invalid/missing")
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val summary = app.vaultMediaRepository.importMedia(album.id, listOf(good1, bad, good2))
        trackImported(summary.importedMediaIds)

        assertEquals(2, summary.successCount)
        assertEquals(1, summary.failureCount)
        assertEquals(0, summary.skippedDueToEntitlement)
        val items = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .filter { it.media.id in createdMediaIds }
        assertEquals(2, items.size)
        items.forEach { assertTrue(it.file.exists()) }
        // No orphan .tmp for failed item
        val root = File(context.filesDir, VaultMediaStorage.DIRECTORY_NAME)
        val tmps = root.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue("orphan tmp=${tmps.map { it.name }}", tmps.isEmpty())
        logEvidence("item_failure", "success=${summary.successCount} fail=${summary.failureCount}")
    }

    @Test
    fun forceNone_existingContentStillManageable() = runBlocking {
        // Seed with VIP
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
        val uri = trackUri(SyntheticMediaFactory.createJpegUri(context, 30))
        val defaultAlbum = app.vaultMediaRepository.ensureDefaultAlbum()
        val seeded = app.vaultMediaRepository.importMedia(defaultAlbum.id, listOf(uri))
        trackImported(seeded.importedMediaIds)
        assertEquals(1, seeded.successCount)
        val mediaId = seeded.importedMediaIds.first()

        val folder = app.vaultMediaRepository.createAlbum("CV_I334B_folder").getOrThrow()
        createdAlbumIds += folder.id

        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceNone)
        assertFalse(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
                is ActionDecision.Allowed,
        )
        // View
        val viewed = app.vaultMediaRepository.observeAlbumMedia(defaultAlbum.id).first()
            .first { it.media.id == mediaId }
        assertTrue(viewed.file.exists())

        // Rename / move / restore / delete folder ops
        assertTrue(app.vaultMediaRepository.renameAlbum(folder.id, "CV_I334B_renamed"))
        val moved = app.vaultMediaRepository.moveMediaToAlbum(listOf(mediaId), folder.id)
        assertTrue(
            moved is com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaMoveResult.Moved,
        )

        val displayName = viewed.media.originalDisplayName
        assertTrue("seed displayName missing", !displayName.isNullOrBlank())
        val restore = app.vaultMediaRepository.restoreMediaToSystem(listOf(mediaId))
        assertTrue("restore success=${restore.successCount} fail=${restore.failureCount}", restore.successCount >= 1)
        val restoredReadable = findRestoredMediaReadable(displayName!!)
        assertTrue("restored system file must be readable name=$displayName", restoredReadable)

        // cleanupMissingMediaRecords must not wipe valid files under ForceNone
        val cleaned = app.vaultMediaRepository.cleanupMissingMediaRecords()
        val stillThere = app.vaultMediaRepository.observeAlbumMedia(folder.id).first()
            .any { it.media.id == mediaId && it.file.exists() }
        assertTrue("media must survive cleanup under ForceNone cleaned=$cleaned", stillThere)

        // Delete media (allowed without entitlement)
        val deleted = app.vaultMediaRepository.deletePrivateMedia(listOf(mediaId))
        assertEquals(1, deleted.successCount)
        createdMediaIds.remove(mediaId)

        assertTrue(
            app.vaultMediaRepository.deleteAlbum(folder.id)
                is com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbumDeleteResult.Deleted,
        )
        createdAlbumIds.remove(folder.id)
        logEvidence("manage_without_entitlement", "view/move/restore/delete/cleanupOK cleaned=$cleaned")
    }

    @Test
    fun vipNavigateAndReturn_listIntact_thenRestoreEntitlementImports() = runBlocking {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val seedUri = trackUri(SyntheticMediaFactory.createJpegUri(context, 40))
        val seeded = app.vaultMediaRepository.importMedia(album.id, listOf(seedUri))
        trackImported(seeded.importedMediaIds)
        val before = snapshotMediaIds()

        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceNone)
        val effects = collectEffects { vm ->
            vm.requestImportToAlbum(album.id)
            SystemClock.sleep(200)
        }
        assertTrue(effects.any { it is PrivateMediaEffect.NavigateToVip })
        // Simulate return from VIP without data mutation
        assertEquals(before, snapshotMediaIds())

        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
        val more = trackUri(SyntheticMediaFactory.createJpegUri(context, 41))
        val again = app.vaultMediaRepository.importMedia(album.id, listOf(more))
        trackImported(again.importedMediaIds)
        assertEquals(1, again.successCount)
        assertTrue(before.all { it in snapshotMediaIds() })
        logEvidence("vip_return_restore", "baselineKept=true newId=${again.importedMediaIds}")
    }

    private fun trackUri(uri: Uri): Uri {
        syntheticUris += uri
        return uri
    }

    private fun trackImported(ids: List<Long>) {
        createdMediaIds.addAll(ids)
    }

    private suspend fun snapshotMediaIds(): Set<Long> =
        app.vaultMediaRepository.observeAlbumSummaries().first()
            .flatMap { summary ->
                app.vaultMediaRepository.observeAlbumMedia(summary.album.id).first().map { it.media.id }
            }
            .toSet()

    private suspend fun snapshotAlbumIds(): Set<Long> =
        app.vaultMediaRepository.observeAlbumSummaries().first().map { it.album.id }.toSet()

    private fun listVaultFiles(): List<File> {
        val root = File(context.filesDir, VaultMediaStorage.DIRECTORY_NAME)
        return root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.sortedBy { it.name }
            .orEmpty()
    }

    private fun findRestoredMediaReadable(displayName: String): Boolean {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
        val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val cursor = context.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(displayName),
            "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC",
        ) ?: return false
        cursor.use {
            if (!it.moveToFirst()) return false
            val id = it.getLong(0)
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id,
            )
            return context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read() >= 0
            } == true
        }
    }

    private fun collectEffects(block: (PrivateMediaViewModel) -> Unit): List<PrivateMediaEffect> {
        val vm = PrivateMediaViewModel(app.vaultMediaRepository, app.entitlementRepository.actionPolicy)
        val received = CopyOnWriteArrayList<PrivateMediaEffect>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val job = scope.launch { vm.effects.collect { received += it } }
        try {
            instrumentation.runOnMainSync { block(vm) }
            SystemClock.sleep(200)
        } finally {
            job.cancel()
            scope.cancel()
        }
        return received
    }

    private suspend fun ensureInitialized() {
        when (val dest = app.onboardingRepository.resolveStartupDestination()) {
            is OnboardingResult.Success -> when (dest.value) {
                StartupDestination.PrivacyConsent -> {
                    assertTrue(app.onboardingRepository.acceptPrivacy() is OnboardingResult.Success)
                    assertTrue(
                        app.onboardingRepository.configurePassword(charArrayOf('1', '3', '5', '7', '9', '0'))
                            is OnboardingResult.Success,
                    )
                }
                StartupDestination.CreatePassword -> {
                    assertTrue(
                        app.onboardingRepository.configurePassword(charArrayOf('1', '3', '5', '7', '9', '0'))
                            is OnboardingResult.Success,
                    )
                }
                StartupDestination.Calculator -> Unit
            }
            is OnboardingResult.Failure -> error("startup failed ${dest.error}")
        }
        app.onPrivacyAccepted()
        assertTrue(app.hasPrivacyConsent())
    }

    private fun assertEmulatorIdentity() {
        val qemu = shell("getprop ro.kernel.qemu").trim()
        assertEquals("1", qemu)
        val avd = shell("getprop ro.boot.qemu.avd_name").trim()
            .ifEmpty { shell("getprop ro.kernel.qemu.avd_name").trim() }
        if (avd.isNotEmpty()) assertEquals("CalculatorVault_Test", avd)
    }

    private fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))).use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) appendLine(line)
            }
        }
    }

    private fun logEvidence(step: String, detail: String) {
        Log.i(TAG, "$step :: $detail")
    }

    private companion object {
        const val TAG = "CV_I334B_EVIDENCE"
    }
}
