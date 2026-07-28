package com.aurora.calculatorvault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppDao
import com.aurora.calculatorvault.feature.hiddenapp.data.HiddenAppEntity

@Database(
    entities = [HiddenAppEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CalculatorVaultDatabase : RoomDatabase() {
    abstract fun hiddenAppDao(): HiddenAppDao
}
