package com.aurora.calculatorvault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.calculatorvault.feature.applock.data.AppLockDao
import com.aurora.calculatorvault.feature.applock.data.AppLockEntryEntity
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppDao
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppEntity
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryDao
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryEntity
import com.aurora.calculatorvault.feature.privatemedia.data.VaultAlbumDao
import com.aurora.calculatorvault.feature.privatemedia.data.VaultAlbumEntity
import com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaDao
import com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaEntity

@Database(
    entities = [
        HiddenAppEntity::class,
        DisguiseEntryEntity::class,
        AppLockEntryEntity::class,
        VaultAlbumEntity::class,
        VaultMediaEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class CalculatorVaultDatabase : RoomDatabase() {
    abstract fun hiddenAppDao(): HiddenAppDao
    abstract fun disguiseEntryDao(): DisguiseEntryDao
    abstract fun appLockDao(): AppLockDao
    abstract fun vaultAlbumDao(): VaultAlbumDao
    abstract fun vaultMediaDao(): VaultMediaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE hidden_apps ADD COLUMN last_opened_at INTEGER DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE hidden_apps ADD COLUMN open_count INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS disguise_entries (
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
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_disguise_entries_package_name " +
                        "ON disguise_entries (package_name)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE disguise_entries ADD COLUMN shortcut_id TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE disguise_entries ADD COLUMN " +
                        "shortcut_request_state TEXT NOT NULL DEFAULT 'NOT_REQUESTED'",
                )
                database.execSQL(
                    "ALTER TABLE disguise_entries ADD COLUMN shortcut_requested_at INTEGER DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE disguise_entries ADD COLUMN shortcut_callback_at INTEGER DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE disguise_entries ADD COLUMN shortcut_last_error TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_disguise_entries_shortcut_id " +
                        "ON disguise_entries (shortcut_id)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_lock_entries (
                        package_name TEXT NOT NULL PRIMARY KEY,
                        app_name_snapshot TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vault_albums (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        is_default INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vault_media (
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
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vault_media_album_id ON vault_media (album_id)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vault_media_imported_at ON vault_media (imported_at)",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE vault_media ADD COLUMN " +
                        "original_removal_state TEXT NOT NULL DEFAULT 'PRESENT'",
                )
            }
        }
    }
}
