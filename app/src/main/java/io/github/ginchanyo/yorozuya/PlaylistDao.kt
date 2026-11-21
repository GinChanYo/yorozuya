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
    suspend fun insertTrack(row: PlaylistTrackEntity): Long

    @Transaction
    // Expression body (= instead of { }): the value of the expression is returned.
    // @Insert with IGNORE returns -1 when the row already exists, so false = track was already in the playlist
    suspend fun addTrack(playlistId: Long, trackId: Long): Boolean =
        insertTrack(PlaylistTrackEntity(playlistId, trackId, nextPosition(playlistId))) != -1L

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId and trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_tracks GROUP BY playlistId")
    fun observeCounts(): Flow<List<PlaylistCount>>

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Int)

    @Transaction
    suspend fun reorder(playlistId: Long, trackIds: List<Long>) {
        trackIds.forEachIndexed { index, trackId ->
            setPosition(playlistId, trackId, index)
        }
    }
}

data class PlaylistCount(val playlistId: Long, val count: Int)