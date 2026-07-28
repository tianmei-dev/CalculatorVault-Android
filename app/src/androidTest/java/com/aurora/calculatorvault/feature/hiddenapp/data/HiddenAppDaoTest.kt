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
