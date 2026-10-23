package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.MediaType

/**
 * Adds a row for every [BuiltInWallpaper] that has none yet, each time the database is opened
 * (a fresh install included). Not a migration, so a built-in added later appears without a schema change.
 * The media columns are unused by built-ins and get placeholder values.
 */
internal object SeedBuiltInWallpapersCallback : RoomDatabase.Callback() {

    private val INSERT_IF_MISSING = """
        INSERT INTO wallpapers (type, original_path, processed_path, thumbnail_path,
            crop_left, crop_top, crop_right, crop_bottom, clip_start_ms, clip_end_ms,
            media_width, media_height, media_rotation, media_duration_ms, is_active, built_in)
        SELECT '${MediaType.IMAGE.name}', '', NULL, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ?
        WHERE NOT EXISTS (SELECT 1 FROM wallpapers WHERE built_in = ?)
    """.trimIndent()

    override fun onOpen(connection: SQLiteConnection) {
        BuiltInWallpaper.entries.forEach { wallpaper ->
            connection.prepare(INSERT_IF_MISSING).use { statement ->
                statement.bindText(1, wallpaper.key)
                statement.bindText(2, wallpaper.key)
                statement.step()
            }
        }
    }
}
