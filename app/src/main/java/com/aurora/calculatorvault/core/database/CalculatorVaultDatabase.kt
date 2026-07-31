package com.aurora.calculatorvault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppDao
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppEntity
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryDao
import com.aurora.calculatorvault.feature.disguise.data.DisguiseEntryEntity

@Database(
    entities = [HiddenAppEntity::class, DisguiseEntryEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class CalculatorVaultDatabase : RoomDatabase() {
    abstract fun hiddenAppDao(): HiddenAppDao
    abstract fun disguiseEntryDao(): DisguiseEntryDao

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
    }
}
