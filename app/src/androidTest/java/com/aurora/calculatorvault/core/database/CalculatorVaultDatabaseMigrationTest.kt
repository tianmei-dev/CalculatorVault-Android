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
            CalculatorVaultDatabase.MIGRATION_4_5,
            CalculatorVaultDatabase.MIGRATION_5_6,
            CalculatorVaultDatabase.MIGRATION_6_7,
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
            CalculatorVaultDatabase.MIGRATION_4_5,
            CalculatorVaultDatabase.MIGRATION_5_6,
            CalculatorVaultDatabase.MIGRATION_6_7,
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
        ).addMigrations(
            CalculatorVaultDatabase.MIGRATION_3_4,
            CalculatorVaultDatabase.MIGRATION_4_5,
            CalculatorVaultDatabase.MIGRATION_5_6,
            CalculatorVaultDatabase.MIGRATION_6_7,
        ).build()

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

    @Test
    fun migrationFrom5To6PreservesExistingRowsAndCreatesVaultMediaTables() = runTest {
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
            db.execSQL("CREATE UNIQUE INDEX index_hidden_apps_package_name ON hidden_apps(package_name)")
            db.execSQL(
                """
                CREATE TABLE disguise_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    target_app_name TEXT NOT NULL,
                    custom_name TEXT NOT NULL,
                    icon_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    shortcut_id TEXT DEFAULT NULL,
                    shortcut_request_state TEXT NOT NULL DEFAULT 'NOT_REQUESTED',
                    shortcut_requested_at INTEGER DEFAULT NULL,
                    shortcut_callback_at INTEGER DEFAULT NULL,
                    shortcut_last_error TEXT DEFAULT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_disguise_entries_package_name ON disguise_entries(package_name)")
            db.execSQL("CREATE UNIQUE INDEX index_disguise_entries_shortcut_id ON disguise_entries(shortcut_id)")
            db.execSQL(
                """
                CREATE TABLE app_lock_entries (
                    package_name TEXT NOT NULL PRIMARY KEY,
                    app_name_snapshot TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO hidden_apps(package_name, app_name_snapshot, added_at, sort_order) " +
                    "VALUES('kept.app', 'Kept', 12, 3)",
            )
            db.execSQL(
                "INSERT INTO app_lock_entries(package_name, app_name_snapshot, enabled, created_at, updated_at) " +
                    "VALUES('locked.app', 'Locked', 1, 20, 21)",
            )
            db.version = 5
        }

        val database = Room.databaseBuilder(
            context,
            CalculatorVaultDatabase::class.java,
            databaseName,
        ).addMigrations(
            CalculatorVaultDatabase.MIGRATION_5_6,
            CalculatorVaultDatabase.MIGRATION_6_7,
        ).build()

        assertEquals("kept.app", database.hiddenAppDao().observeAll().first().single().packageName)
        assertEquals(1, database.appLockDao().observeAllEntries().first().size)
        val albumId = database.vaultAlbumDao().insert(
            com.aurora.calculatorvault.feature.privatemedia.data.VaultAlbumEntity(
                name = "私密相册",
                isDefault = true,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        assertEquals(true, albumId > 0)
        database.vaultMediaDao().insertMedia(
            com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaEntity(
                albumId = albumId,
                mediaType = "IMAGE",
                privateFileName = "a.jpg",
                originalDisplayName = "a.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 10,
                width = 1,
                height = 1,
                durationMs = null,
                importedAt = 101,
                originalUri = "content://example/a",
            ),
        )
        assertEquals(1, database.vaultMediaDao().observeMediaCount().first())
        assertEquals(
            "PRESENT",
            database.vaultMediaDao().getMediaById(1)?.originalRemovalState,
        )
        database.close()
    }

    @Test
    fun migrationFrom6To7AddsOriginalRemovalState() = runTest {
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
            db.execSQL("CREATE UNIQUE INDEX index_hidden_apps_package_name ON hidden_apps(package_name)")
            db.execSQL(
                """
                CREATE TABLE disguise_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    package_name TEXT NOT NULL,
                    target_app_name TEXT NOT NULL,
                    custom_name TEXT NOT NULL,
                    icon_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    shortcut_id TEXT DEFAULT NULL,
                    shortcut_request_state TEXT NOT NULL DEFAULT 'NOT_REQUESTED',
                    shortcut_requested_at INTEGER DEFAULT NULL,
                    shortcut_callback_at INTEGER DEFAULT NULL,
                    shortcut_last_error TEXT DEFAULT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_disguise_entries_package_name ON disguise_entries(package_name)")
            db.execSQL("CREATE UNIQUE INDEX index_disguise_entries_shortcut_id ON disguise_entries(shortcut_id)")
            db.execSQL(
                """
                CREATE TABLE app_lock_entries (
                    package_name TEXT NOT NULL PRIMARY KEY,
                    app_name_snapshot TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE vault_albums (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    is_default INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE vault_media (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    album_id INTEGER NOT NULL,
                    media_type TEXT NOT NULL,
                    private_file_name TEXT NOT NULL,
                    original_display_name TEXT,
                    mime_type TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    width INTEGER,
                    height INTEGER,
                    duration_ms INTEGER,
                    imported_at INTEGER NOT NULL,
                    original_uri TEXT,
                    FOREIGN KEY(album_id) REFERENCES vault_albums(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_vault_media_album_id ON vault_media(album_id)")
            db.execSQL("CREATE INDEX index_vault_media_imported_at ON vault_media(imported_at)")
            db.execSQL(
                "INSERT INTO vault_albums(id, name, is_default, created_at, updated_at) " +
                    "VALUES(1, '私密相册', 1, 100, 100)",
            )
            db.execSQL(
                """
                INSERT INTO vault_media(
                    album_id, media_type, private_file_name, original_display_name, mime_type,
                    size_bytes, width, height, duration_ms, imported_at, original_uri
                ) VALUES(1, 'IMAGE', 'a.jpg', 'a.jpg', 'image/jpeg', 10, 1, 1, NULL, 101, 'content://example/a')
                """.trimIndent(),
            )
            db.version = 6
        }

        val database = Room.databaseBuilder(
            context,
            CalculatorVaultDatabase::class.java,
            databaseName,
        ).addMigrations(CalculatorVaultDatabase.MIGRATION_6_7).build()

        val row = database.vaultMediaDao().getMediaById(1)
        assertEquals("a.jpg", row?.privateFileName)
        assertEquals("PRESENT", row?.originalRemovalState)
        database.close()
    }
}
