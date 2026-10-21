package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.editor.export.VideoExporter
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWallpaperUseCaseImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val saved = mutableListOf<NewWallpaper>()
    private val exports = mutableListOf<String>()
    private var exportResult: List<ExportStatus> = listOf(ExportStatus.InProgress(50), ExportStatus.Success)

    private val useCase = CreateWallpaperUseCaseImpl(
        mediaStorage = object : MediaStorage {
            override val tempMediaFile = File("temp/media")
            override val tempThumbnailFile = File("temp/thumbnail")
            override fun copyToTemp(uri: String) = tempMediaFile.path
            override fun persistTempMedia() = "originals/a"
            override fun persistTempThumbnail() = "thumbnails/a"
            override fun delete(path: String) = Unit
        },
        videoExporter = object : VideoExporter {
            override fun export(inputPath: String, outputPath: String, crop: CropRect, clip: ClipRange): Flow<ExportStatus> {
                exports += outputPath
                return flowOf(*exportResult.toTypedArray())
            }
        },
        saveWallpaperUseCase = object : SaveWallpaperUseCase {
            override suspend fun invoke(wallpaper: NewWallpaper) { saved += wallpaper }
        },
        dispatchersProvider = object : DispatchersProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private val video = PreparedMedia("temp/media", MediaType.VIDEO, MediaInfo(1920, 1080, 90, 10_000))
    private val crop = CropRect(0, 0, 100, 200)
    private val clip = ClipRange(1_000, 5_000)

    @Test
    fun `video without pre-crop is saved right away and cropped later in GL`() = runTest(dispatcher) {
        val statuses = useCase(video, crop, clip, preCrop = false).toList()

        assertEquals(listOf<ExportStatus>(ExportStatus.Success), statuses)
        assertTrue(exports.isEmpty())
        assertNull(saved.single().processedPath)
        assertEquals("thumbnails/a", saved.single().thumbnailPath)
        assertEquals(video.mediaInfo, saved.single().mediaInfo)
    }

    @Test
    fun `pre-cropped video is saved only after the export succeeds`() = runTest(dispatcher) {
        val statuses = useCase(video, crop, clip, preCrop = true).toList()

        assertEquals(listOf(ExportStatus.InProgress(50), ExportStatus.Success), statuses)
        assertEquals(listOf("originals/a_cropped.mp4"), exports)
        assertEquals("originals/a_cropped.mp4", saved.single().processedPath)
    }

    @Test
    fun `failed export saves nothing`() = runTest(dispatcher) {
        exportResult = listOf(ExportStatus.Error(IllegalStateException("encoder")))

        useCase(video, crop, clip, preCrop = true).toList()

        assertTrue(saved.isEmpty())
    }

    @Test
    fun `image never gets a processed copy or a video thumbnail`() = runTest(dispatcher) {
        val image = PreparedMedia("temp/media", MediaType.IMAGE, MediaInfo(800, 600, 0, 0))

        useCase(image, crop, clip, preCrop = true).toList()

        assertTrue(exports.isEmpty())
        assertNull(saved.single().processedPath)
        assertEquals("", saved.single().thumbnailPath)
    }
}
