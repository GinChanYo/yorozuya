package io.github.ginchanyo.yorozuya

sealed interface QueueSource {
    data object Library: QueueSource
    data object Favorites: QueueSource
    data class Playlist(val id: Long): QueueSource
}
