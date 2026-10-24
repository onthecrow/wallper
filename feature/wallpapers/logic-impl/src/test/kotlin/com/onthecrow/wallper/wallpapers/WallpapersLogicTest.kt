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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersLogicTest {

    private val dao = FakeWallpaperDao()
    private val storage = FakeMediaStorage()
    private val repository = WallpapersRepository(dao)
    private val activations = WallpaperActivations()
    private val add = AddWallpaperUseCaseImpl(repository)
    private val update = UpdateWallpaperUseCaseImpl(storage, repository)

    private val media = NewWallpaper.Media(
        type = MediaType.VIDEO,
        originalPath = "originals/a",
        thumbnailPath = "thumbnails/a",
        crop = CropRect(10, 20, 110, 220),
        clip = ClipRange(500, 1500),
        mediaInfo = MediaInfo(1920, 1080, 90, 3000),
    )
    private val processed = NewWallpaper.ProcessedVideo(path = "originals/b.mp4", thumbnailPath = "thumbnails/b")

    @Test
    fun `media round-trips through the entity`() = runTest {
        add(media)

        val saved = assertIs<Wallpaper.Media>(ObserveWallpapersUseCaseImpl(repository)().first().single())

        assertEquals(media.originalPath, saved.originalPath)
        assertEquals(media.crop, saved.crop)
        assertEquals(media.clip, saved.clip)
        assertEquals(media.mediaInfo, saved.mediaInfo)
        assertEquals(false, saved.isActive)
    }

    @Test
    fun `a processed video keeps no original and plays as is`() = runTest {
        add(processed)
        ActivateWallpaperUseCaseImpl(repository, activations)(1)

        assertNull(dao.rows.value.single().originalPath)
        assertEquals(
            Wallpaper.ProcessedVideo(1, "originals/b.mp4", "thumbnails/b", isActive = true),
            GetWallpaperUseCaseImpl(repository)(1),
        )
        assertEquals(
            WallpaperSpec.ProcessedVideo("originals/b.mp4", "thumbnails/b"),
            ActiveWallpaperSourceImpl(repository, activations).activeWallpaperFlow.first(),
        )
    }

    @Test
    fun `a processed video from before v4 is still final although it kept the original`() = runTest {
        dao.rows.value = listOf(mediaEntity(id = 1).copy(processedPath = "originals/a_cropped.mp4"))

        assertIs<Wallpaper.ProcessedVideo>(GetWallpaperUseCaseImpl(repository)(1))

        DeleteWallpaperUseCaseImpl(storage, repository)(1)
        assertEquals(setOf("originals/a", "originals/a_cropped.mp4", "thumbnails/a"), storage.deletedPaths.toSet())
    }

    @Test
    fun `there is nothing to get for an unknown id`() = runTest {
        assertNull(GetWallpaperUseCaseImpl(repository)(7))
    }

    @Test
    fun `activation keeps a single active wallpaper and feeds the engine`() = runTest {
        add(media)
        add(media.copy(originalPath = "originals/b"))
        val source = ActiveWallpaperSourceImpl(repository, activations)
        assertNull(source.activeWallpaperFlow.first())

        ActivateWallpaperUseCaseImpl(repository, activations)(1)
        ActivateWallpaperUseCaseImpl(repository, activations)(2)

        assertEquals(listOf(2), dao.rows.value.filter { it.isActive }.map { it.id })
        assertEquals("originals/b", assertIs<WallpaperSpec.Media>(source.activeWallpaperFlow.first()).sourcePath)
    }

    @Test
    fun `every pick reaches the engine, the active wallpaper picked again too`() = runTest {
        add(media)
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
        add(media)
        add(processed)
        // Seeded on the first open after the update, so it comes after the user's rows in the table.
        dao.rows.value += builtInEntity(id = 3, key = BuiltInWallpaper.BLACK_HOLE.key)

        val wallpapers = ObserveWallpapersUseCaseImpl(repository)().first()

        assertEquals(Wallpaper.BuiltIn(3, BuiltInWallpaper.BLACK_HOLE, isActive = false), wallpapers[0])
        assertEquals(listOf(1, 2), wallpapers.drop(1).map { it.id })
    }

    @Test
    fun `a built-in unknown to this version is skipped`() = runTest {
        add(media)
        dao.rows.value += builtInEntity(id = 2, key = "from_a_newer_version", isActive = true)

        assertEquals(listOf(1), ObserveWallpapersUseCaseImpl(repository)().first().map { it.id })
        assertNull(ActiveWallpaperSourceImpl(repository, activations).activeWallpaperFlow.first())
    }

    @Test
    fun `an edit keeps the place, the activity and the files`() = runTest {
        add(media)
        add(processed)
        ActivateWallpaperUseCaseImpl(repository, activations)(1)

        update(1, media.copy(crop = CropRect(0, 0, 50, 100), clip = ClipRange(0, 1000)))

        val edited = assertIs<Wallpaper.Media>(ObserveWallpapersUseCaseImpl(repository)().first().first())
        assertEquals(1, edited.id)
        assertEquals(CropRect(0, 0, 50, 100), edited.crop)
        assertEquals(ClipRange(0, 1000), edited.clip)
        assertEquals(true, edited.isActive)
        assertEquals(emptyList(), storage.deletedPaths)
    }

    @Test
    fun `an edit into a processed video deletes the original once it is saved`() = runTest {
        add(media)
        ActivateWallpaperUseCaseImpl(repository, activations)(1)

        update(1, NewWallpaper.ProcessedVideo(path = "originals/c.mp4", thumbnailPath = media.thumbnailPath))

        assertEquals(
            WallpaperSpec.ProcessedVideo("originals/c.mp4", "thumbnails/a"),
            ActiveWallpaperSourceImpl(repository, activations).activeWallpaperFlow.first(),
        )
        assertEquals(listOf("originals/a"), storage.deletedPaths)
    }

    @Test
    fun `update refuses built-ins`() = runTest {
        val builtIn = builtInEntity(id = 1, key = BuiltInWallpaper.BLACK_HOLE.key)
        dao.rows.value = listOf(builtIn)

        assertFailsWith<IllegalStateException> { update(1, media) }

        assertEquals(listOf(builtIn), dao.rows.value)
    }

    @Test
    fun `delete removes the record and every file`() = runTest {
        add(media)

        DeleteWallpaperUseCaseImpl(storage, repository)(1)

        assertEquals(emptyList(), dao.rows.value)
        assertEquals(listOf("originals/a", "thumbnails/a"), storage.deletedPaths)
    }

    @Test
    fun `delete refuses built-ins`() = runTest {
        val builtIn = builtInEntity(id = 1, key = BuiltInWallpaper.BLACK_HOLE.key)
        dao.rows.value = listOf(builtIn)

        DeleteWallpaperUseCaseImpl(storage, repository)(1)

        assertEquals(listOf(builtIn), dao.rows.value)
        assertEquals(emptyList(), storage.deletedPaths)
    }

    private fun mediaEntity(id: Int) = WallpaperEntity(
        id = id,
        type = MediaType.VIDEO,
        originalPath = "originals/a",
        processedPath = null,
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
        isActive = false,
    )

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
