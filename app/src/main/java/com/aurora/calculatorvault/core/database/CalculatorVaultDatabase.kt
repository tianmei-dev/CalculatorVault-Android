package com.aurora.calculatorvault.core.database

import androidx.room.RoomDatabase

/**
 * Room 数据库扩展入口。
 *
 * Phase 1 尚无真实 Entity，因此暂不添加 @Database 注解，也不创建数据库实例。
 * 首个业务 Entity 确定后再补齐 schema、DAO 与迁移策略。
 */
abstract class CalculatorVaultDatabase : RoomDatabase()

