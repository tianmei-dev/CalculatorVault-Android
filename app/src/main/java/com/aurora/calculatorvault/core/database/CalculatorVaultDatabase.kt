package com.aurora.calculatorvault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppDao
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppEntity

@Database(
    entities = [HiddenAppEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CalculatorVaultDatabase : RoomDatabase() {
    abstract fun hiddenAppDao(): HiddenAppDao

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
    }
}
