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
        ).addMigrations(
            CalculatorVaultDatabase.MIGRATION_1_2,
            CalculatorVaultDatabase.MIGRATION_2_3,
            CalculatorVaultDatabase.MIGRATION_3_4,
        ).build()

        val row = database.hiddenAppDao().observeAll().first().single()
        assertEquals("kept.app", row.packageName)
        assertEquals(12, row.addedAt)
        assertEquals(3, row.sortOrder)
        assertEquals(null, row.lastOpenedAt)
        assertEquals(0, row.openCount)
        assertEquals(1, database.hiddenAppDao().markOpened("kept.app", 99))
        database.close()
    }

    @Test
    fun migrationFrom2To3PreservesHiddenAppsAndCreatesDisguiseTable() = runTest {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE hidden_apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    app_name_snapshot TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL,
                    last_opened_at INTEGER DEFAULT NULL,
                    open_count INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_hidden_apps_package_name ON hidden_apps(package_name)",
            )
            db.execSQL(
                "INSERT INTO hidden_apps(package_name, app_name_snapshot, added_at, sort_order) " +
                    "VALUES('kept.app', 'Kept', 12, 3)",
            )
            db.version = 2
        }

        val database = Room.databaseBuilder(
            context,
            CalculatorVaultDatabase::class.java,
            databaseName,
        ).addMigrations(
            CalculatorVaultDatabase.MIGRATION_2_3,
            CalculatorVaultDatabase.MIGRATION_3_4,
        ).build()

        assertEquals("kept.app", database.hiddenAppDao().observeAll().first().single().packageName)
        val id = database.disguiseEntryDao().insert(
            com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryEntity(
                packageName = "target.app",
                targetAppName = "Target",
                customName = "Files",
                iconId = "Files",
                createdAt = 20,
                updatedAt = 20,
            ),
        )
        assertEquals(1, database.disguiseEntryDao().observeAll().first().size)
        assertEquals(true, id > 0)
        database.close()
    }

    @Test
    fun migrationFrom3To4PreservesExistingConfigurationAndAddsShortcutDefaults() = runTest {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE hidden_apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    app_name_snapshot TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL,
                    last_opened_at INTEGER DEFAULT NULL,
                    open_count INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_hidden_apps_package_name ON hidden_apps(package_name)",
            )
            db.execSQL(
                """
                CREATE TABLE disguise_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    target_app_name TEXT NOT NULL,
                    custom_name TEXT NOT NULL,
                    icon_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX index_disguise_entries_package_name " +
                    "ON disguise_entries(package_name)",
            )
            db.execSQL(
                """
                INSERT INTO disguise_entries(
                    package_name, target_app_name, custom_name, icon_id, created_at, updated_at
                ) VALUES('target.app', 'Target', 'Files', 'Files', 20, 21)
                """.trimIndent(),
            )
            db.version = 3
        }

        val database = Room.databaseBuilder(
            context,
            CalculatorVaultDatabase::class.java,
            databaseName,
        ).addMigrations(CalculatorVaultDatabase.MIGRATION_3_4).build()

        val row = database.disguiseEntryDao().observeAll().first().single()
        assertEquals("target.app", row.packageName)
        assertEquals("Files", row.customName)
        assertEquals("Files", row.iconId)
        assertEquals(null, row.shortcutId)
        assertEquals("NOT_REQUESTED", row.shortcutRequestState)
        assertEquals(null, row.shortcutRequestedAt)
        assertEquals(1, database.disguiseEntryDao().setShortcutIdIfMissing(row.id, "cv_disguise_a"))
        assertEquals(0, database.disguiseEntryDao().setShortcutIdIfMissing(row.id, "cv_disguise_b"))
        assertEquals("cv_disguise_a", database.disguiseEntryDao().findById(row.id)?.shortcutId)
        database.close()
    }
}
