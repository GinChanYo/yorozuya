package io.github.ginchanyo.yorozuya

sealed interface PlaylistTarget {
    data object Favorites : PlaylistTarget
    data class Custom(val id: Long) : PlaylistTarget
}