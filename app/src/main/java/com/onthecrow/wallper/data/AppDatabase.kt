package com.onthecrow.wallper.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WallpaperEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
}