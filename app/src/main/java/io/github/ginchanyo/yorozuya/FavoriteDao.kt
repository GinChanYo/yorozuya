package io.github.ginchanyo.yorozuya

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT trackID FROM favorites")
    fun observeIds() : Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun remove(trackId: Long)

    @Query("SELECT trackId FROM favorites ORDER BY addedAt Desc")
    fun observeIdsNewestFirst(): Flow<List<Long>>
}