package com.aurora.calculatorvault.feature.privatemedia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultAlbumDao {
    @Query("SELECT * FROM vault_albums WHERE is_default = 1 LIMIT 1")
    fun observeDefaultAlbum(): Flow<VaultAlbumEntity?>

    @Query("SELECT * FROM vault_albums WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultAlbum(): VaultAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: VaultAlbumEntity): Long
}
