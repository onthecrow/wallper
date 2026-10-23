package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersLogicTest {

    private val dao = FakeWallpaperDao()
    private val storage = FakeMediaStorage()
    private val repository = WallpapersRepository(dao)
    private val activations = WallpaperActivations()

    private val newWallpaper = NewWallpaper(
        type = MediaType.VIDEO,
        originalPath = "originals/a",
        processedPath = "originals/a_cropped.mp4",
        thumbnailPath = "thumbnails/a",
        crop = CropRect(10, 20, 110, 220),
        clip = ClipRange(500, 1500),
        mediaInfo = MediaInfo(1920, 1080, 90, 3000),
    )

    @Test
    fun `saved wallpaper round-trips through the entity`() = runTest {
        SaveWallpaperUseCaseImpl(repository)(newWallpaper)

        val saved = assertIs<Wallpaper.Media>(ObserveWallpapersUseCaseImpl(repository)().first().single())

        assertEquals(newWallpaper.crop, saved.crop)
        assertEquals(newWallpaper.clip, saved.clip)
        assertEquals(newWallpaper.mediaInfo, saved.mediaInfo)
        assertEquals(newWallpaper.processedPath, saved.processedPath)
        assertEquals(false, saved.isActive)
    }

    @Test
    fun `activation keeps a single active wallpaper and feeds the engine`() = runTest {
        val save = SaveWallpaperUseCaseImpl(repository)
        save(newWallpaper)
        save(newWallpaper.copy(originalPath = "originals/b"))
        val source = ActiveWallpaperSourceImpl(repository, activations)
        assertNull(source.activeWallpaperFlow.first())

        ActivateWallpaperUseCaseImpl(repository, activations)(1)
        ActivateWallpaperUseCaseImpl(repository, activations)(2)

        assertEquals(listOf(2), dao.rows.value.filter { it.isActive }.map { it.id })
        assertEquals("originals/b", assertIs<WallpaperSpec.Media>(source.activeWallpaperFlow.first()).sourcePath)
    }

    @Test
    fun `every pick reaches the engine, the active wallpaper picked again too`() = runTest {
        SaveWallpaperUseCaseImpl(repository)(newWallpaper)
        val source = ActiveWallpaperSourceImpl(repository, activations)
        val picks = mutableListOf<Unit>()
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.activations.toList(picks) }

        ActivateWallpaperUseCaseImpl(repository, activations)(1)
        ActivateWallpaperUseCaseImpl(repository, activations)(1)

        assertEquals(2, picks.size)
        collecting.cancel()
    }

    @Test
    fun `an active built-in feeds the engine its key`() = runTest {
        dao.rows.value = listOf(builtInEntity(id = 1, key = BuiltInWallpaper.BLACK_HOLE.key))

        ActivateWallpaperUseCaseImpl(repository, activations)(1)

        assertEquals(
            WallpaperSpec.BuiltIn(BuiltInWallpaper.BLACK_HOLE),
            ActiveWallpaperSourceImpl(repository, activations).activeWallpaperFlow.first(),
        )
    }

    @Test
    fun `built-ins come first and the rest keep their order`() = runTest {
        val save = SaveWallpaperUseCaseImpl(repository)
        save(newWallpaper)
        save(newWallpaper.copy(originalPath = "originals/b"))
        // Seeded on the first open after the update, so it comes after the user's rows in the table.
        dao.rows.value += builtInEntity(id = 3, key = BuiltInWallpaper.BLACK_HOLE.key)

        val wallpapers = ObserveWallpapersUseCaseImpl(repository)().first()

        assertEquals(Wallpaper.BuiltIn(3, BuiltInWallpaper.BLACK_HOLE, isActive = false), wallpapers[0])
        assertEquals(listOf(1, 2), wallpapers.drop(1).map { assertIs<Wallpaper.Media>(it).id })
    }

    @Test
    fun `a built-in unknown to this version is skipped`() = runTest {
        SaveWallpaperUseCaseImpl(repository)(newWallpaper)
        dao.rows.value += builtInEntity(id = 2, key = "from_a_newer_version", isActive = true)

        assertEquals(listOf(1), ObserveWallpapersUseCaseImpl(repository)().first().map { it.id })
        assertNull(ActiveWallpaperSourceImpl(repository, activations).activeWallpaperFlow.first())
    }

    @Test
    fun `delete removes the record and every file`() = runTest {
        SaveWallpaperUseCaseImpl(repository)(newWallpaper)

        DeleteWallpaperUseCaseImpl(storage, repository)(1)

        assertEquals(emptyList(), dao.rows.value)
        assertEquals(listOf("originals/a", "originals/a_cropped.mp4", "thumbnails/a"), storage.deletedPaths)
    }

    @Test
    fun `delete refuses built-ins`() = runTest {
        val builtIn = builtInEntity(id = 1, key = BuiltInWallpaper.BLACK_HOLE.key)
        dao.rows.value = listOf(builtIn)

        DeleteWallpaperUseCaseImpl(storage, repository)(1)

        assertEquals(listOf(builtIn), dao.rows.value)
        assertEquals(emptyList(), storage.deletedPaths)
    }

    /** What the seeding puts into the table: placeholders in the media columns. */
    private fun builtInEntity(id: Int, key: String, isActive: Boolean = false) = WallpaperEntity(
        id = id,
        type = MediaType.IMAGE,
        originalPath = "",
        processedPath = null,
        thumbnailPath = "",
        cropLeft = 0,
        cropTop = 0,
        cropRight = 0,
        cropBottom = 0,
        clipStartMs = 0,
        clipEndMs = 0,
        mediaWidth = 0,
        mediaHeight = 0,
        mediaRotation = 0,
        mediaDurationMs = 0,
        isActive = isActive,
        builtIn = key,
    )
}
