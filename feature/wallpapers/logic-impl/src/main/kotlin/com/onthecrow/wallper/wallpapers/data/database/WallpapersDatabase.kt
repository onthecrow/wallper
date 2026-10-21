package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WallpaperEntity::class], version = 1, exportSchema = true)
internal abstract class WallpapersDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao

    companion object {
        /** A new file name: the pre-migration `wallper-db` has an incompatible schema and is left untouched. */
        const val NAME = "wallpapers.db"
    }
}
