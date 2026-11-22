package io.github.ginchanyo.yorozuya

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
// Version of the file format, not of the app. Bumped only when the structure changes,
// so a future import can tell "old file - read it the old way"
const val BACKUP_VERSION = 1

@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val favorites: List<BackupTrackRef> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList()
)

// How a track is identified across devices: MediaStore id is useless on another phone,
// so the file is found by path + name first, and by tags as a fallback
@Serializable
data class BackupTrackRef(
    val fileName: String,
    val relativePath: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0
)

@Serializable
data class BackupPlaylist(
    val name: String,
    val createdAt: Long,
    val tracks: List<BackupTrackRef> = emptyList()
)

// Extension function: called like a method of Track - track.toRef().
// Expression body (= ...) instead of { return ... } - for a function that is a single expression
fun Track.toRef() = BackupTrackRef(
    fileName = fileName,
    relativePath = relativePath,
    title = title,
    artist = artist,
    durationMs = duration
)

// Collects favorites and playlists from the database into one BackupFile.
// tracksById - the library loaded from MediaStore (AppScaffold already has it): turns stored ids into file info.
// Ids missing from the library (file deleted or moved) are skipped - there's nothing to identify them by.
// suspend - DAO calls are suspend, so this runs inside a coroutine (e.g. scope.launch on a button click)
suspend fun buildBackup(
    favoriteDao: FavoriteDao,
    playlistDao: PlaylistDao,
    tracksById: Map<Long, Track>
): BackupFile {
    // getAll() is already sorted by position, so list order = favorites order; positions themselves aren't saved
    val favorites = favoriteDao.getAll().mapNotNull { tracksById[it.trackId]?.toRef() }
    val playlists = playlistDao.getAllPlaylists().map { playlist ->
        BackupPlaylist(
            name = playlist.name,
            createdAt = playlist.createdAt,
            tracks = playlistDao.getTrackIds(playlist.id).mapNotNull { tracksById[it]?.toRef() }
        )
    }
    return BackupFile(favorites = favorites, playlists = playlists)
}

val backupJson = Json {
    encodeDefaults = true     // always write version, exportedAt, empty lists
    ignoreUnknownKeys = true  // a newer file with extra fields can still be read by this version
    prettyPrint = true        // readable file, easy to check by eye
}