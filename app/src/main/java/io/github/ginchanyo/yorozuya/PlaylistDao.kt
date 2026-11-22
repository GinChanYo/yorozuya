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

    // New tracks go on top: one less than the current smallest position (negatives are fine, ORDER BY still works).
    // Empty playlist: MIN is NULL, COALESCE falls back to 1, so the first track gets 0
    @Query("SELECT COALESCE(MIN(position), 1) - 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun topPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(row: PlaylistTrackEntity): Long

    @Transaction
    // Expression body (= instead of { }): the value of the expression is returned.
    // @Insert with IGNORE returns -1 when the row already exists, so false = track was already in the playlist
    suspend fun addTrack(playlistId: Long, trackId: Long): Boolean =
        insertTrack(PlaylistTrackEntity(playlistId, trackId, topPosition(playlistId))) != -1L

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId and trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    // All (playlist, track) rows. Counts are computed in Kotlin, not with COUNT(*): rows whose track is missing
    // from the library (file renamed, SD card removed) stay in the table but must not be counted
    @Query("SELECT * FROM playlist_tracks")
    fun observeAllTracks(): Flow<List<PlaylistTrackEntity>>

    // Mirror of topPosition: one more than the current largest position, for appending to the end.
    // Empty playlist: MAX is NULL, COALESCE falls back to -1, so the first track gets 0
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun bottomPosition(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    // One-off reads for backup (Backup.kt): suspend + plain List instead of Flow,
    // because export/import needs a single snapshot, not updates over time
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    // Same order as observeTrackIds, so the backup keeps the playlist order
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getTrackIds(playlistId: Long): List<Long>

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Int)

    // Writes positions 0..n-1 in the given order. @Transaction - all updates commit together or none do,
    // so a crash mid-way can't leave the playlist half-reordered (and Room's Flow emits once, not n times)
    @Transaction
    suspend fun reorder(playlistId: Long, trackIds: List<Long>) {
        trackIds.forEachIndexed { index, trackId ->
            setPosition(playlistId, trackId, index)
        }
    }
}
