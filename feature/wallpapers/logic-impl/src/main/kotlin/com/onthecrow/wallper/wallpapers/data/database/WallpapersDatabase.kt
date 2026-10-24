package com.onthecrow.wallper.wallpapers.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WallpaperEntity::class], version = 4, exportSchema = true)
internal abstract class WallpapersDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao

    companion object {
        /** A new file name: the pre-migration `wallper-db` has an incompatible schema and is left untouched. */
        const val NAME = "wallpapers.db"

        /** The builder with every migration and the built-ins seeding; shared by the app and the migration test. */
        fun builder(context: Context): Builder<WallpapersDatabase> =
            Room.databaseBuilder(context, WallpapersDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SeedBuiltInWallpapersCallback)
    }
}
