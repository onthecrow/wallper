package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface WallpaperDao {

    /** Built-ins first, then the user's wallpapers; each in the order they were added. */
    @Query("SELECT * FROM wallpapers ORDER BY built_in IS NULL, id")
    fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<WallpaperEntity?>

    @Query("SELECT * FROM wallpapers WHERE id = :id")
    suspend fun get(id: Int): WallpaperEntity

    @Insert
    suspend fun insert(wallpaper: WallpaperEntity)

    @Delete
    suspend fun delete(wallpaper: WallpaperEntity)

    /** Makes [id] the only active wallpaper in one statement. */
    @Query("UPDATE wallpapers SET is_active = (id = :id)")
    suspend fun activate(id: Int)
}
