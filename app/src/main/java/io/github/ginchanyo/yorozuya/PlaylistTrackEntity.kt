package io.github.ginchanyo.yorozuya

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)

data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)