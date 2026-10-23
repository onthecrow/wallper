package com.onthecrow.wallper.wallpapers.data.database

import android.content.ContextWrapper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migrations on the JVM: the old database is created from the exported schema, then opened by the app's
 * own [WallpapersDatabase.builder] on a bundled SQLite. On open Room runs the migrations, validates the
 * result against the current entities and calls the seeding callback, as on a device.
 *
 * `MigrationTestHelper` isn't used: on Android it needs an `Instrumentation` to read the schemas.
 */
class WallpapersMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context by lazy { DatabaseDirContext(folder.root) }

    @Test
    fun `migration 1 to 2 keeps the wallpapers and adds the built-ins`() = runTest {
        createDatabase(version = 1) { connection ->
            connection.execSQL(
                """
                INSERT INTO wallpapers (id, type, original_path, processed_path, thumbnail_path,
                    crop_left, crop_top, crop_right, crop_bottom, clip_start_ms, clip_end_ms,
                    media_width, media_height, media_rotation, media_duration_ms, is_active)
                VALUES (1, 'VIDEO', 'originals/a', 'originals/a_cropped.mp4', 'thumbnails/a',
                    10, 20, 110, 220, 500, 1500, 1920, 1080, 90, 3000, 1)
                """.trimIndent()
            )
        }

        val rows = withDatabase { it.wallpaperDao().observeAll().first() }

        val media = rows.single { it.builtIn == null }
        assertEquals(
            WallpaperEntity(
                id = 1,
                type = MediaType.VIDEO,
                originalPath = "originals/a",
                processedPath = "originals/a_cropped.mp4",
                thumbnailPath = "thumbnails/a",
                cropLeft = 10,
                cropTop = 20,
                cropRight = 110,
                cropBottom = 220,
                clipStartMs = 500,
                clipEndMs = 1500,
                mediaWidth = 1920,
                mediaHeight = 1080,
                mediaRotation = 90,
                mediaDurationMs = 3000,
                isActive = true,
            ),
            media,
        )
        assertBuiltInsSeeded(rows)
    }

    @Test
    fun `migration 2 to 3 leaves one row per built-in`() = runTest {
        createDatabase(version = 2) { connection ->
            listOf(1, 2).forEach { id ->
                connection.execSQL(
                    """
                    INSERT INTO wallpapers (id, type, original_path, processed_path, thumbnail_path,
                        crop_left, crop_top, crop_right, crop_bottom, clip_start_ms, clip_end_ms,
                        media_width, media_height, media_rotation, media_duration_ms, is_active, built_in)
                    VALUES ($id, 'IMAGE', '', NULL, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ${if (id == 2) 1 else 0},
                        '${BuiltInWallpaper.BLACK_HOLE.key}')
                    """.trimIndent()
                )
            }
        }

        val rows = withDatabase { it.wallpaperDao().observeAll().first() }

        assertBuiltInsSeeded(rows)
        assertEquals(1, rows.single().id)
    }

    @Test
    fun `a fresh install gets the built-ins`() = runTest {
        val rows = withDatabase { it.wallpaperDao().observeAll().first() }

        assertBuiltInsSeeded(rows)
    }

    @Test
    fun `reopening does not duplicate or reset the built-ins`() = runTest {
        val blackHole = withDatabase { database ->
            val dao = database.wallpaperDao()
            dao.observeAll().first().single { it.builtIn == BuiltInWallpaper.BLACK_HOLE.key }
                .also { dao.activate(it.id) }
        }

        val rows = withDatabase { it.wallpaperDao().observeAll().first() }

        assertBuiltInsSeeded(rows)
        assertEquals(blackHole.copy(isActive = true), rows.single())
    }

    private fun assertBuiltInsSeeded(rows: List<WallpaperEntity>) {
        val builtIns = rows.filter { it.builtIn != null }
        assertEquals(BuiltInWallpaper.entries.map { it.key }, builtIns.map { it.builtIn })
        builtIns.forEach { row ->
            assertEquals(MediaType.IMAGE, row.type)
            assertEquals("", row.originalPath)
            assertEquals(null, row.processedPath)
        }
    }

    private suspend fun <T> withDatabase(block: suspend (WallpapersDatabase) -> T): T {
        val database = WallpapersDatabase.builder(context).setDriver(BundledSQLiteDriver()).build()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    /** Creates the database file the way Room [version] did: its tables and identity from the exported schema. */
    private fun createDatabase(version: Int, fill: (SQLiteConnection) -> Unit) {
        val schema = Json.parseToJsonElement(File(SCHEMAS_DIR, "$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        val path = context.getDatabasePath(WallpapersDatabase.NAME).path
        BundledSQLiteDriver().open(path).use { connection ->
            schema.getValue("entities").jsonArray.map { it.jsonObject }.forEach { entity ->
                val table = entity.getValue("tableName").jsonPrimitive.content
                val statements = listOf(entity.getValue("createSql")) +
                    entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql") }
                statements.forEach { connection.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = $version")
            fill(connection)
        }
    }

    /** Just enough of a `Context` for Room: the database directory, no system services. */
    private class DatabaseDirContext(private val dir: File) : ContextWrapper(null) {
        override fun getDatabasePath(name: String): File = File(dir, name)
        override fun getSystemService(name: String): Any? = null
    }

    private companion object {
        /** Gradle runs unit tests in the module directory. */
        val SCHEMAS_DIR = File("schemas/${WallpapersDatabase::class.qualifiedName}")
    }
}
