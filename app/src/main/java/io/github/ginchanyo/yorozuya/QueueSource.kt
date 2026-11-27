package io.github.ginchanyo.yorozuya

sealed interface QueueSource {
    data object Library: QueueSource
    data object Favorites: QueueSource
    data class Playlist(val id: Long): QueueSource
}

// Serializes this [QueueSource] into a string representation suitable for storing in SharedPreferences
// (e.g., "library", "favorites", or "playlist:42").
fun QueueSource.toPrefString() : String = when(this) {
    QueueSource.Library -> "library"
    QueueSource.Favorites -> "favorites"
    is QueueSource.Playlist -> "playlist:$id"
}

// Parses a [QueueSource] from its string representation stored in SharedPreferences.
// @return The corresponding [QueueSource], or `null` if [text] is null, malformed, or unrecognized.
fun queueSourceFromPrefString(text: String?): QueueSource? {
    if (text == null) return null

    return when {
        text == "library" -> QueueSource.Library
        text == "favorites" -> QueueSource.Favorites
        text.startsWith("playlist:") -> text
            .removePrefix("playlist:")
            .toLongOrNull()
            ?.let { QueueSource.Playlist(it)
            }
        else -> null
    }
}
