package io.github.ginchanyo.yorozuya
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
// MediaStore is the database of the system itself, and I treat is as a SQL table.


// Context is access to the android system from the inside of an app. Through it resources (strings, art),
// system services are taken, permissions checked, and it this case ContentResolver is resolved.
// MainActivity is context by itself - that's why loadTracks(this) gets "this".
fun loadTracks(context: Context): List<Track>{
    // Here Uri is not the address to track, but to the whole table with audios. Like table name in SQL
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
// projection - a list of columns (SELECT analog)
    val projection  = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.RELATIVE_PATH
    )
// selection - condition (WHERE)
// Condition IS_MUSIC != 0 removed ringtones and notification sounds, so as system sounds. Otherwise, media player would have alarms as well
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    val tracks = mutableListOf<Track>()
    // ContentResolver is a "middle man". In android one app does not get access to the files of another man directly.
    // It addresses to ContentResolver using ContentProvider. MediaStore is the system provider.
    // That means that contentResolver.query(...) is literally sentence: "System, execute this request to table with audios and return cursor."
    // Same mechanism works with contacts, calendar and will appear more.
    context.contentResolver
        .query(collection, projection, selection, null, sortOrder)
        ?.use {cursor ->
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()){
                val id = cursor.getLong(idCol)
                tracks += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleCol) ?: "Unknown Name",
                    artist = cursor.getString(artistCol),
                    album = cursor.getString(albumCol),
                    duration = cursor.getLong(durationCol),
                    relativePath = cursor.getString(pathCol) ?: "",
                )
            }
        }
    return tracks
}