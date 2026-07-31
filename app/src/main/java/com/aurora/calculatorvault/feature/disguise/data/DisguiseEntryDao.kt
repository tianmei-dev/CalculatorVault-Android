package com.aurora.calculatorvault.feature.disguise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DisguiseEntryDao {
    @Query("SELECT * FROM disguise_entries")
    fun observeAll(): Flow<List<DisguiseEntryEntity>>

    @Query("SELECT * FROM disguise_entries WHERE id = :id")
    suspend fun findById(id: Long): DisguiseEntryEntity?

    @Query("SELECT * FROM disguise_entries WHERE shortcut_id = :shortcutId")
    suspend fun findByShortcutId(shortcutId: String): DisguiseEntryEntity?

    @Insert
    suspend fun insert(entry: DisguiseEntryEntity): Long

    @Update
    suspend fun update(entry: DisguiseEntryEntity): Int

    @Query("DELETE FROM disguise_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """
        UPDATE disguise_entries
        SET shortcut_id = :shortcutId
        WHERE id = :id AND shortcut_id IS NULL
        """,
    )
    suspend fun setShortcutIdIfMissing(id: Long, shortcutId: String): Int

    @Query(
        """
        UPDATE disguise_entries
        SET shortcut_request_state = :state,
            shortcut_requested_at = :requestedAt,
            shortcut_last_error = :lastError
        WHERE id = :id
        """,
    )
    suspend fun updateShortcutRequest(
        id: Long,
        state: String,
        requestedAt: Long?,
        lastError: String?,
    ): Int

    @Query(
        """
        UPDATE disguise_entries
        SET shortcut_request_state = 'LAUNCHER_ACCEPTED',
            shortcut_callback_at = :callbackAt,
            shortcut_last_error = NULL
        WHERE shortcut_id = :shortcutId
        """,
    )
    suspend fun markShortcutAccepted(shortcutId: String, callbackAt: Long): Int
}
