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

// Finds which track in the current library each backup entry refers to.
// Three attempts, from strict to loose: same folder + same file name, then the file name alone
// (library was reorganised), then tags (file was renamed but is the same recording)
class TrackMatcher(library: List<Track>) {
    // associateBy - one value per key (folder + name is unique on a device).
    // groupBy - a list per key: several files may share a name ("01.mp3") or tags (same song in two qualities)
    private val byFullPath = library.associateBy { it.relativePath + it.fileName }
    private val byFileName = library.groupBy { it.fileName }
    private val byTags = library.groupBy { tagKey(it.title, it.artist, it.duration) }

    // ?: chain - try the next index only if the previous one gave null.
    // singleOrNull - take a candidate only if it's the only one; with several, skipping beats guessing wrong
    fun find(ref: BackupTrackRef): Track? =
        byFullPath[ref.relativePath + ref.fileName]
            ?: byFileName[ref.fileName]?.singleOrNull()
            ?: byTags[tagKey(ref.title, ref.artist, ref.durationMs)]?.singleOrNull()

    // lowercase - tags may differ in case; / 1000 - re-encoded copies differ by milliseconds
    private fun tagKey(title: String, artist: String?, durationMs: Long) =
        "${title.lowercase()}|${artist?.lowercase() ?: ""}|${durationMs / 1000}"
}

// matched / missing - counted over all entries (favorites + every playlist), shown to the user after import
data class RestoreResult(val matched: Int, val missing: Int, val playlists: Int)

// Merge, not replace: nothing existing is deleted. Favorites already present are skipped by IGNORE.
// A playlist with the same name is extended instead of duplicated, so importing twice changes nothing,
// and a re-import repairs links broken by a renamed file (the track with its new id is appended)
suspend fun restoreBackup(
    backup: BackupFile,
    favoriteDao: FavoriteDao,
    playlistDao: PlaylistDao,
    library: List<Track>
): RestoreResult {
    val matcher = TrackMatcher(library)
    var matched = 0
    var missing = 0

    // Favorites: imported ones go above the existing ones, keeping the file's order.
    // topPosition() = current smallest position - 1, so the last imported track gets it and the rest go above
    val favoriteTracks = backup.favorites.mapNotNull { matcher.find(it) }
    matched += favoriteTracks.size
    missing += backup.favorites.size - favoriteTracks.size
    val start = favoriteDao.topPosition() - favoriteTracks.size + 1
    // forEachIndexed - like forEach, but also gives the index (for (i, x) in enumerate(...) in Python)
    favoriteTracks.forEachIndexed { index, track ->
        favoriteDao.add(FavoriteEntity(trackId = track.id, position = start + index))
    }

    // Existing playlists by name. If several share a name, the oldest one wins (getAllPlaylists is sorted by createdAt):
    // distinctBy keeps the first item for each key. toMutableMap - new playlists are added during the loop,
    // so two same-named playlists inside one backup end up merged too
    val byName = playlistDao.getAllPlaylists()
        .distinctBy { it.name }
        .associate { it.name to it.id }
        .toMutableMap()

    for (playlist in backup.playlists) {
        // getOrPut - returns the existing id, or runs the block, stores its result under the key and returns it
        val id = byName.getOrPut(playlist.name) {
            playlistDao.createPlaylist(PlaylistEntity(name = playlist.name, createdAt = playlist.createdAt))
        }
        val tracks = playlist.tracks.mapNotNull { matcher.find(it) }
        matched += tracks.size
        missing += playlist.tracks.size - tracks.size
        // Appended after what's already there, in the file's order. insertTrack, not addTrack - addTrack puts
        // a track on top. IGNORE returns -1 for a track already in the playlist: it keeps its place,
        // and the position counter moves only on a real insert, so there are no gaps
        var position = playlistDao.bottomPosition(id)
        for (track in tracks) {
            val inserted = playlistDao.insertTrack(PlaylistTrackEntity(playlistId = id, trackId = track.id, position = position))
            if (inserted != -1L) position++
        }
    }
    return RestoreResult(matched = matched, missing = missing, playlists = backup.playlists.size)
}
