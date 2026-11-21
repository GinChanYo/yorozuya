package io.github.ginchanyo.yorozuya

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity (tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    // defaultValue - SQL DEFAULT for rows that existed before this column; AutoMigration 2->3 needs it.
    // "= 0" is only the Kotlin default for new FavoriteEntity(...) objects
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)
