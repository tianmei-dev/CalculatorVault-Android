package com.aurora.calculatorvault.feature.privatemedia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultAlbumDao {
    @Query("SELECT * FROM vault_albums ORDER BY is_default DESC, updated_at DESC, id ASC")
    fun observeAllAlbums(): Flow<List<VaultAlbumEntity>>

    @Query("SELECT * FROM vault_albums WHERE is_default = 1 LIMIT 1")
    fun observeDefaultAlbum(): Flow<VaultAlbumEntity?>

    @Query("SELECT * FROM vault_albums WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultAlbum(): VaultAlbumEntity?

    @Query("SELECT * FROM vault_albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbum(albumId: Long): VaultAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: VaultAlbumEntity): Long

    @Query("UPDATE vault_albums SET name = :name, updated_at = :updatedAt WHERE id = :albumId")
    suspend fun renameAlbum(albumId: Long, name: String, updatedAt: Long): Int

    @Query("UPDATE vault_albums SET updated_at = :updatedAt WHERE id = :albumId")
    suspend fun touchAlbum(albumId: Long, updatedAt: Long): Int

    @Query("DELETE FROM vault_albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long): Int
}
