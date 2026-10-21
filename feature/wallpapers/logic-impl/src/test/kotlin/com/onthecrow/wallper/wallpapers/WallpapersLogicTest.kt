package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WallpapersLogicTest {

    private val dao = FakeWallpaperDao()
    private val storage = FakeMediaStorage()
    private val repository = WallpapersRepository(dao)

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

        val saved = ObserveWallpapersUseCaseImpl(repository)().first().single()

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
        val source = ActiveWallpaperSourceImpl(repository)
        assertNull(source.activeWallpaperFlow.first())

        ActivateWallpaperUseCaseImpl(repository)(1)
        ActivateWallpaperUseCaseImpl(repository)(2)

        assertEquals(listOf(2), dao.rows.value.filter { it.isActive }.map { it.id })
        assertEquals("originals/b", source.activeWallpaperFlow.first()?.sourcePath)
    }

    @Test
    fun `delete removes the record and every file`() = runTest {
        SaveWallpaperUseCaseImpl(repository)(newWallpaper)

        DeleteWallpaperUseCaseImpl(storage, repository)(1)

        assertEquals(emptyList(), dao.rows.value)
        assertEquals(listOf("originals/a", "originals/a_cropped.mp4", "thumbnails/a"), storage.deletedPaths)
    }
}
