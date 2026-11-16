package io.github.ginchanyo.yorozuya

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity (tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
