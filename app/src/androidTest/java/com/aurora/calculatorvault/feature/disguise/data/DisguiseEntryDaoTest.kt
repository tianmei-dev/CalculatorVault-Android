package com.aurora.calculatorvault.feature.disguise.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisguiseEntryDaoTest {
    private lateinit var database: CalculatorVaultDatabase
    private lateinit var dao: DisguiseEntryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CalculatorVaultDatabase::class.java,
        ).build()
        dao = database.disguiseEntryDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun insertUpdateAndDeleteConfiguration() = runTest {
        val id = dao.insert(entity())
        val inserted = dao.observeAll().first().single()
        assertEquals("文件", inserted.customName)

        assertEquals(
            1,
            dao.update(inserted.copy(customName = "工作", iconId = "Tools", updatedAt = 20)),
        )
        assertEquals("工作", dao.findById(id)?.customName)
        assertEquals("Tools", dao.findById(id)?.iconId)

        assertEquals(1, dao.deleteById(id))
        assertEquals(emptyList<DisguiseEntryEntity>(), dao.observeAll().first())
    }

    @Test
    fun sameTargetCanHaveMultipleIndependentConfigurations() = runTest {
        dao.insert(entity(customName = "文件"))
        dao.insert(entity(customName = "工具"))

        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun shortcutIdIsStableAndCallbackIsIdempotent() = runTest {
        val id = dao.insert(entity())
        assertEquals(1, dao.setShortcutIdIfMissing(id, "cv_disguise_first"))
        assertEquals(0, dao.setShortcutIdIfMissing(id, "cv_disguise_second"))
        assertEquals("cv_disguise_first", dao.findById(id)?.shortcutId)

        assertEquals(
            1,
            dao.updateShortcutRequest(
                id,
                "REQUEST_SUBMITTED",
                100,
                null,
            ),
        )
        assertEquals(1, dao.markShortcutAccepted("cv_disguise_first", 200))
        assertEquals(1, dao.markShortcutAccepted("cv_disguise_first", 200))
        val updated = dao.findByShortcutId("cv_disguise_first")
        assertEquals("LAUNCHER_ACCEPTED", updated?.shortcutRequestState)
        assertEquals(200L, updated?.shortcutCallbackAt)
    }

    @Test
    fun repositoryBackfillsOldIdsOnceAndEditKeepsTheSameId() = runTest {
        val oldId = dao.insert(entity())
        val generated = ArrayDeque(listOf("cv_disguise_old", "cv_disguise_next"))
        val repository = DisguiseEntryRepository(
            database = database,
            scanner = EmptyScanner,
            shortcutIdGenerator = ShortcutIdGenerator { generated.removeFirst() },
        )

        assertEquals("cv_disguise_old", repository.ensureShortcutId(oldId))
        assertEquals("cv_disguise_old", repository.ensureShortcutId(oldId))
        assertEquals(
            true,
            repository.update(
                id = oldId,
                packageName = "target.app",
                targetAppName = "Target",
                customName = "Updated",
                iconId = DisguiseIconId.Tools,
            ),
        )
        assertEquals("cv_disguise_old", repository.findById(oldId)?.shortcutId)

        val secondId = repository.create(
            packageName = "second.app",
            targetAppName = "Second",
            customName = "Second",
            iconId = DisguiseIconId.Files,
        )
        assertEquals("cv_disguise_next", repository.findById(secondId)?.shortcutId)
    }

    private object EmptyScanner : InstalledAppScanner {
        override suspend fun scan() = emptyList<InstalledApp>()
        override suspend fun resolve(packageName: String): InstalledApp? = null
    }

    private fun entity(customName: String = "文件") = DisguiseEntryEntity(
        packageName = "target.app",
        targetAppName = "Target",
        customName = customName,
        iconId = "Files",
        createdAt = 10,
        updatedAt = 10,
    )
}
