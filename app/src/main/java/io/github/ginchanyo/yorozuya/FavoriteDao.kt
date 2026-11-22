package io.github.ginchanyo.yorozuya

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // addedAt DESC - tie-break for rows migrated from version 2: they all got position 0, newest shown first
    @Query("SELECT trackID FROM favorites ORDER BY position, addedAt DESC")
    fun observeIds() : Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun remove(trackId: Long)

    @Query("SELECT trackId FROM favorites ORDER BY addedAt Desc")
    fun observeIdsNewestFirst(): Flow<List<Long>>

    // Same trick as PlaylistDao.topPosition: new favorite goes above the current top one
    @Query("SELECT COALESCE(MIN(position), 1) - 1 FROM favorites")
    suspend fun topPosition(): Int

    @Query("UPDATE favorites SET position = :position WHERE trackId = :trackId")
    suspend fun setPosition(trackId: Long, position: Int)

    @Query("SELECT * FROM favorites ORDER BY position, addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    // Same as PlaylistDao.reorder: positions 0..n-1 in one transaction
    @Transaction
    suspend fun reorder(trackIds: List<Long>) {
        trackIds.forEachIndexed { index, trackId ->
            setPosition(trackId, index)
        }
    }
}