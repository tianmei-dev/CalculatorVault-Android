package com.aurora.calculatorvault.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorVaultDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-1-2.db"

    @Before
    fun prepare() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFrom1To2PreservesRowsAndAddsRecentDefaults() = runTest {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hidden_apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    app_name_snapshot TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_hidden_apps_package_name " +
                    "ON hidden_apps (package_name)",
            )
            db.execSQL(
                "INSERT INTO hidden_apps(package_name, app_name_snapshot, added_at, sort_order) " +
                    "VALUES('kept.app', 'Kept', 12, 3)",
            )
            db.version = 1
        }

        val database = Room.databaseBuilder(
            context,
            CalculatorVaultDatabase::class.java,
            databaseName,
        ).addMigrations(CalculatorVaultDatabase.MIGRATION_1_2).build()

        val row = database.hiddenAppDao().observeAll().first().single()
        assertEquals("kept.app", row.packageName)
        assertEquals(12, row.addedAt)
        assertEquals(3, row.sortOrder)
        assertEquals(null, row.lastOpenedAt)
        assertEquals(0, row.openCount)
        assertEquals(1, database.hiddenAppDao().markOpened("kept.app", 99))
        database.close()
    }
}
