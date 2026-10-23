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
