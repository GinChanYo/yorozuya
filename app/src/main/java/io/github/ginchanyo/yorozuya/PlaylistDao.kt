package io.github.ginchanyo.yorozuya

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId =:playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(row: PlaylistTrackEntity)

    @Transaction
    suspend fun addTrack(playlistId: Long, trackId: Long){
        insertTrack(PlaylistTrackEntity(playlistId, trackId, nextPosition(playlistId)))
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId and trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)
}