package com.aurora.calculatorvault.feature.privatemedia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultMediaDao {
    @Query("SELECT * FROM vault_media WHERE album_id = :albumId ORDER BY imported_at DESC, id DESC")
    fun observeMediaByAlbum(albumId: Long): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media ORDER BY imported_at DESC, id DESC")
    fun observeAllMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media ORDER BY imported_at DESC, id DESC")
    suspend fun observeAllMediaSnapshot(): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<Long>): List<VaultMediaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedia(media: VaultMediaEntity): Long

    @Query("SELECT COUNT(*) FROM vault_media")
    fun observeMediaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vault_media WHERE album_id = :albumId")
    suspend fun countMediaByAlbum(albumId: Long): Int

    @Query("SELECT COUNT(*) FROM vault_media WHERE media_type = :mediaType")
    fun observeMediaCountByType(mediaType: String): Flow<Int>

    @Query("UPDATE vault_media SET album_id = :targetAlbumId WHERE id IN (:ids)")
    suspend fun moveMediaToAlbum(ids: List<Long>, targetAlbumId: Long): Int

    @Query("DELETE FROM vault_media WHERE id = :id")
    suspend fun deleteMediaRecord(id: Long): Int

    @Query("DELETE FROM vault_media WHERE id IN (:ids)")
    suspend fun deleteMediaRecords(ids: List<Long>): Int

    @Query("UPDATE vault_media SET original_removal_state = :state WHERE id IN (:ids)")
    suspend fun updateOriginalRemovalState(ids: List<Long>, state: String): Int
}
