package io.github.ginchanyo.yorozuya

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    // autoGenerate = true: ID is given by DB on insert. That's why default is 0
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)