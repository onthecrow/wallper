package com.onthecrow.wallper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {

    @Query("SELECT * FROM wallpaperentity")
    fun getAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpaperentity WHERE is_active = 1 LIMIT 1")
    fun getActive(): Flow<WallpaperEntity>

    @Query("SELECT * FROM wallpaperentity WHERE uid =:uid")
    suspend fun getWallpaper(uid: Int): WallpaperEntity

    @Insert
    suspend fun insertAll(vararg wallpapers: WallpaperEntity)

    @Delete
    suspend fun delete(wallpaper: WallpaperEntity)

    @Update
    suspend fun update(wallpaper: WallpaperEntity)
}