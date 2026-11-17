package io.github.ginchanyo.yorozuya

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class YorozuyaDatabase : RoomDatabase(){
    abstract fun favoriteDao() : FavoriteDao
    abstract fun playlistDao() : PlaylistDao
    companion object {
        @Volatile
        private var instance: YorozuyaDatabase? = null

        fun get(context: Context): YorozuyaDatabase =
            instance ?: synchronized(this){
                instance ?: build(context.applicationContext).also {instance = it}
            }
        private fun build(appContext: Context): YorozuyaDatabase =
            Room.databaseBuilder<YorozuyaDatabase>(
                context = appContext,
                name = appContext.getDatabasePath("yorozuya.db").absolutePath
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}