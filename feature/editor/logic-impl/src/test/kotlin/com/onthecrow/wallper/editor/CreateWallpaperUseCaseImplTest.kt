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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWallpaperUseCaseImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val saved = mutableListOf<NewWallpaper>()
    private val exports = mutableListOf<Pair<String, String>>()
    private val persisted = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private var exportResult: List<ExportStatus> = listOf(ExportStatus.InProgress(50), ExportStatus.Success)
    private var saveError: Exception? = null

    private val useCase = CreateWallpaperUseCaseImpl(
        mediaStorage = object : MediaStorage {
            override val tempMediaFile = File("editor/media")
            override val tempThumbnailFile = File("editor/thumbnail")
            override val tempProcessedFile = File("editor/processed.mp4")
            override fun copyToTemp(uri: String) = tempMediaFile.path
            override fun persistTempMedia() = "originals/a".also(persisted::add)
            override fun persistTempProcessed() = "originals/b.mp4".also(persisted::add)
            override fun persistTempThumbnail() = "thumbnails/a".also(persisted::add)
            override fun clearTemp() = Unit
            override fun delete(path: String) {
                deleted += path
            }
        },
        videoExporter = object : VideoExporter {
            override fun export(inputPath: String, outputPath: String, crop: CropRect, clip: ClipRange): Flow<ExportStatus> {
                exports += inputPath to outputPath
                return flowOf(*exportResult.toTypedArray())
            }
        },
        saveWallpaperUseCase = object : SaveWallpaperUseCase {
            override suspend fun invoke(wallpaper: NewWallpaper) {
                saveError?.let { throw it }
                saved += wallpaper
            }
        },
        dispatchersProvider = object : DispatchersProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private val video = PreparedMedia("editor/media", MediaType.VIDEO, MediaInfo(1920, 1080, 90, 10_000))
    private val crop = CropRect(0, 0, 100, 200)
    private val clip = ClipRange(1_000, 5_000)

    @Test
    fun `video without pre-crop is saved right away and cropped later in GL`() = runTest(dispatcher) {
        val statuses = useCase(video, crop, clip, preCrop = false).toList()

        assertEquals(listOf<ExportStatus>(ExportStatus.Success), statuses)
        assertTrue(exports.isEmpty())
        assertEquals("originals/a", saved.single().originalPath)
        assertNull(saved.single().processedPath)
        assertEquals("thumbnails/a", saved.single().thumbnailPath)
        assertEquals(video.mediaInfo, saved.single().mediaInfo)
    }

    @Test
    fun `pre-cropped video is exported in scratch and saved only after the export succeeds`() = runTest(dispatcher) {
        val statuses = useCase(video, crop, clip, preCrop = true).toList()

        assertEquals(listOf(ExportStatus.InProgress(50), ExportStatus.Success), statuses)
        assertEquals(listOf("editor/media" to "editor/processed.mp4"), exports)
        assertEquals("originals/b.mp4", saved.single().processedPath)
    }

    @Test
    fun `failed export saves nothing and leaves nothing in permanent storage`() = runTest(dispatcher) {
        exportResult = listOf(ExportStatus.Error(IllegalStateException("encoder")))

        useCase(video, crop, clip, preCrop = true).toList()

        assertTrue(saved.isEmpty())
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun `a wallpaper that can't be saved takes its copies with it`() = runTest(dispatcher) {
        saveError = IllegalStateException("database")

        assertFailsWith<IllegalStateException> { useCase(video, crop, clip, preCrop = true).toList() }

        assertEquals(listOf("originals/a", "originals/b.mp4", "thumbnails/a"), persisted)
        assertEquals(persisted, deleted.filterNot { it == "editor/processed.mp4" })
    }

    @Test
    fun `image never gets a processed copy or a video thumbnail`() = runTest(dispatcher) {
        val image = PreparedMedia("editor/media", MediaType.IMAGE, MediaInfo(800, 600, 0, 0))

        useCase(image, crop, clip, preCrop = true).toList()

        assertTrue(exports.isEmpty())
        assertNull(saved.single().processedPath)
        assertEquals("", saved.single().thumbnailPath)
    }
}
