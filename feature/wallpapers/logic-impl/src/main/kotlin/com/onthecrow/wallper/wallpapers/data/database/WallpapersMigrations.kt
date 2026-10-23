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
