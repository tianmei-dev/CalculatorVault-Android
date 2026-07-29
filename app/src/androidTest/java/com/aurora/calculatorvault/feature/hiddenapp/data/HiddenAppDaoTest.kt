package com.aurora.calculatorvault.feature.hiddenapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HiddenAppDaoTest {
    private lateinit var database: CalculatorVaultDatabase
    private lateinit var dao: HiddenAppDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            CalculatorVaultDatabase::class.java,
        ).build()
        dao = database.hiddenAppDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun uniquePackageIndexIgnoresDuplicateWithoutReplacingExistingRecord() = runTest {
        val original = entity(name = "Original", addedAt = 10, sortOrder = 2)
        val duplicate = entity(name = "Replacement", addedAt = 99, sortOrder = 9)

        val firstResult = dao.insertAll(listOf(original))
        val duplicateResult = dao.insertAll(listOf(duplicate))
        val stored = dao.observeAll().first().single()

        assertEquals(1, firstResult.count { it != -1L })
        assertEquals(listOf(-1L), duplicateResult)
        assertEquals("Original", stored.appNameSnapshot)
        assertEquals(10, stored.addedAt)
        assertEquals(2, stored.sortOrder)
    }

    @Test
    fun observeAllOrdersBySortOrderThenAddedAt() = runTest {
        dao.insertAll(
            listOf(
                entity("late", "Late", addedAt = 20, sortOrder = 1),
                entity("first", "First", addedAt = 30, sortOrder = 0),
                entity("early", "Early", addedAt = 10, sortOrder = 1),
            ),
        )

        assertEquals(
            listOf("first", "early", "late"),
            dao.observeAll().first().map(HiddenAppEntity::packageName),
        )
    }

    @Test
    fun markOpenedIncrementsCountAndRecentQueryOrdersByNewest() = runTest {
        dao.insertAll(
            listOf(
                entity("a", "A", 1, 0),
                entity("b", "B", 2, 1),
            ),
        )

        assertEquals(1, dao.markOpened("a", 10))
        assertEquals(1, dao.markOpened("b", 20))
        assertEquals(1, dao.markOpened("a", 30))

        val recent = dao.observeRecentlyOpened(10).first()
        assertEquals(listOf("a", "b"), recent.map(HiddenAppEntity::packageName))
        assertEquals(2, recent.first().openCount)
    }

    @Test
    fun clearRecentKeepsHiddenRowsAndResetsOnlyHistoryFields() = runTest {
        dao.insertAll(
            listOf(
                entity("a", "A", 1, 0).copy(lastOpenedAt = 10, openCount = 2),
                entity("b", "B", 2, 1),
            ),
        )

        assertEquals(1, dao.clearRecentHistory())

        val rows = dao.observeAll().first()
        assertEquals(2, rows.size)
        assertEquals(emptyList<HiddenAppEntity>(), dao.observeRecentlyOpened(10).first())
        assertEquals(listOf(0, 0), rows.map(HiddenAppEntity::openCount))
    }

    @Test
    fun batchDeleteRemovesOnlyRequestedRows() = runTest {
        dao.insertAll(
            listOf(
                entity("a", "A", 1, 0),
                entity("b", "B", 2, 1),
                entity("c", "C", 3, 2),
            ),
        )

        assertEquals(2, dao.deleteByPackageNames(listOf("a", "c")))
        assertEquals(listOf("b"), dao.observeAll().first().map(HiddenAppEntity::packageName))
    }

    @Test
    fun roomStoreUpdatesManualOrderTransactionallyWithoutChangingHistory() = runTest {
        dao.insertAll(
            listOf(
                entity("a", "A", 1, 0).copy(lastOpenedAt = 10, openCount = 2),
                entity("b", "B", 2, 1),
                entity("c", "C", 3, 2),
            ),
        )
        val store = RoomHiddenAppStore(database)

        assertEquals(true, store.updateManualOrder(listOf("c", "a", "b")))
        val rows = dao.observeAll().first()
        assertEquals(listOf("c", "a", "b"), rows.map(HiddenAppEntity::packageName))
        assertEquals(10L, rows.first { it.packageName == "a" }.lastOpenedAt)
        assertEquals(2, rows.first { it.packageName == "a" }.openCount)
    }

    private fun entity(
        packageName: String = "same.package",
        name: String,
        addedAt: Long,
        sortOrder: Int,
    ) = HiddenAppEntity(
        packageName = packageName,
        appNameSnapshot = name,
        addedAt = addedAt,
        sortOrder = sortOrder,
    )
}
