package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v2: `built_in` marks the rows of built-in wallpapers. The rows themselves are added on open. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE wallpapers ADD COLUMN built_in TEXT")
    }
}

/**
 * v3: `built_in` is unique, so a built-in has one row whatever happens. Any duplicates (there should be
 * none) keep their first row.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            DELETE FROM wallpapers WHERE built_in IS NOT NULL AND id NOT IN
                (SELECT MIN(id) FROM wallpapers WHERE built_in IS NOT NULL GROUP BY built_in)
            """.trimIndent()
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wallpapers_built_in` ON `wallpapers` (`built_in`)")
    }
}

/**
 * v4: `original_path` may be NULL: a processed video keeps no original. SQLite can't change a column, so
 * the table is rebuilt with the same rows.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE `wallpapers_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL,
                `original_path` TEXT, `processed_path` TEXT, `thumbnail_path` TEXT NOT NULL,
                `crop_left` INTEGER NOT NULL, `crop_top` INTEGER NOT NULL, `crop_right` INTEGER NOT NULL,
                `crop_bottom` INTEGER NOT NULL, `clip_start_ms` INTEGER NOT NULL, `clip_end_ms` INTEGER NOT NULL,
                `media_width` INTEGER NOT NULL, `media_height` INTEGER NOT NULL, `media_rotation` INTEGER NOT NULL,
                `media_duration_ms` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `built_in` TEXT)
            """.trimIndent()
        )
        val columns = """
            `id`, `type`, `original_path`, `processed_path`, `thumbnail_path`, `crop_left`, `crop_top`, `crop_right`,
            `crop_bottom`, `clip_start_ms`, `clip_end_ms`, `media_width`, `media_height`, `media_rotation`,
            `media_duration_ms`, `is_active`, `built_in`
        """.trimIndent()
        connection.execSQL("INSERT INTO `wallpapers_new` ($columns) SELECT $columns FROM `wallpapers`")
        connection.execSQL("DROP TABLE `wallpapers`")
        connection.execSQL("ALTER TABLE `wallpapers_new` RENAME TO `wallpapers`")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wallpapers_built_in` ON `wallpapers` (`built_in`)")
    }
}
