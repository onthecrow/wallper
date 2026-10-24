package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.editor.domain.model.EditedWallpaper
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.wallpapers.domain.GetWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PrepareSavedMediaUseCaseImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val storage = FakeMediaStorage()
    private var wallpaper: Wallpaper? = null

    private val useCase = PrepareSavedMediaUseCaseImpl(
        getWallpaperUseCase = object : GetWallpaperUseCase {
            override suspend fun invoke(id: Int) = wallpaper?.takeIf { it.id == id }
        },
        mediaStorage = storage,
        dispatchersProvider = object : DispatchersProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    @Test
    fun `a saved wallpaper is edited from its original, crop and trim`() = runTest(dispatcher) {
        wallpaper = Wallpaper.Media(
            id = 3,
            type = MediaType.VIDEO,
            originalPath = "originals/a",
            thumbnailPath = "thumbnails/a",
            crop = CropRect(10, 20, 110, 220),
            clip = ClipRange(500, 1500),
            mediaInfo = MediaInfo(1920, 1080, 90, 3000),
            isActive = true,
        )

        val media = useCase(3).getOrThrow()

        assertEquals(
            PreparedMedia(
                path = "originals/a",
                type = MediaType.VIDEO,
                mediaInfo = MediaInfo(1920, 1080, 90, 3000),
                edited = EditedWallpaper(3, "thumbnails/a", CropRect(10, 20, 110, 220), ClipRange(500, 1500)),
            ),
            media,
        )
        assertEquals(1, storage.clears)
        assertTrue(storage.persisted.isEmpty())
    }

    @Test
    fun `a processed video has no original to edit`() = runTest(dispatcher) {
        wallpaper = Wallpaper.ProcessedVideo(id = 3, path = "originals/b.mp4", thumbnailPath = "thumbnails/b", isActive = false)

        assertTrue(useCase(3).isFailure)
    }

    @Test
    fun `a wallpaper that is gone can't be edited`() = runTest(dispatcher) {
        assertTrue(useCase(3).isFailure)
    }
}
