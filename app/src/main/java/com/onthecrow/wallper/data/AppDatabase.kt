package com.onthecrow.wallper.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.onthecrow.wallper.data.tipeconverters.WallperTypeConverters

@Database(entities = [WallpaperEntity::class], version = 1)
@TypeConverters(WallperTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
}