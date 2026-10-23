package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.MediaType

/**
 * Adds a row for every [BuiltInWallpaper] that has none yet, each time the database is opened
 * (a fresh install included). Not a migration, so a built-in added later appears without a schema change.
 * The unique `built_in` index makes the insert a no-op for one that exists. The media columns are unused
 * by built-ins and get placeholder values.
 */
internal object SeedBuiltInWallpapersCallback : RoomDatabase.Callback() {

    private val INSERT_IF_MISSING = """
        INSERT OR IGNORE INTO wallpapers (type, original_path, processed_path, thumbnail_path,
            crop_left, crop_top, crop_right, crop_bottom, clip_start_ms, clip_end_ms,
            media_width, media_height, media_rotation, media_duration_ms, is_active, built_in)
        VALUES ('${MediaType.IMAGE.name}', '', NULL, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ?)
    """.trimIndent()

    override fun onOpen(connection: SQLiteConnection) {
        BuiltInWallpaper.entries.forEach { wallpaper ->
            connection.prepare(INSERT_IF_MISSING).use { statement ->
                statement.bindText(1, wallpaper.key)
                statement.step()
            }
        }
    }
}
