package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.Tint
import com.onthecrow.wallper.editor.domain.model.EditedWallpaper
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.editor.export.VideoExporter
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.AddWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.UpdateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaveWallpaperUseCaseImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val storage = FakeMediaStorage()
    private val added = mutableListOf<NewWallpaper>()
    private val updated = mutableListOf<Pair<Int, NewWallpaper>>()
    private val exports = mutableListOf<Pair<String, String>>()
    private val exportedTints = mutableListOf<Tint?>()
    private val exportedAreas = mutableListOf<CropRect>()
    private var encodes = true
    private var exportResult: List<ExportStatus> = listOf(ExportStatus.InProgress(50), ExportStatus.Success)
    private var saveError: Exception? = null

    /** Holds the add up until it completes. */
    private var addGate: CompletableDeferred<Unit>? = null
    private val activated = mutableListOf<Int>()
    private val appSettings = MutableStateFlow(AppSettings())

    private val useCase = SaveWallpaperUseCaseImpl(
        mediaStorage = storage,
        videoExporter = object : VideoExporter {
            override fun export(
                inputPath: String,
                outputPath: String,
                crop: CropRect,
                clip: ClipRange,
                tint: Tint?,
            ): Flow<ExportStatus> {
                exports += inputPath to outputPath
                exportedTints += tint
                exportedAreas += crop
                return flowOf(*exportResult.toTypedArray())
            }

            override fun canEncode(width: Int, height: Int) = encodes
        },
        addWallpaperUseCase = object : AddWallpaperUseCase {
            override suspend fun invoke(wallpaper: NewWallpaper): Int {
                addGate?.await()
                saveError?.let { throw it }
                added += wallpaper
                return NEW_ID
            }
        },
        updateWallpaperUseCase = object : UpdateWallpaperUseCase {
            override suspend fun invoke(id: Int, wallpaper: NewWallpaper) {
                saveError?.let { throw it }
                updated += id to wallpaper
            }
        },
        activateWallpaperUseCase = object : ActivateWallpaperUseCase {
            override suspend fun invoke(id: Int) {
                activated += id
            }
        },
        appSettings = object : AppSettingsSource {
            override val settings = appSettings
            override fun update(transform: (AppSettings) -> AppSettings) = appSettings.update(transform)
        },
        dispatchersProvider = object : DispatchersProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private val video = PreparedMedia("editor/media", MediaType.VIDEO, MediaInfo(1920, 1080, 90, 10_000))
    private val savedVideo = PreparedMedia(
        path = "originals/a",
        type = MediaType.VIDEO,
        mediaInfo = video.mediaInfo,
        edited = EditedWallpaper(id = 7, thumbnailPath = "thumbnails/old", crop = CropRect(0, 0, 1, 1), clip = ClipRange(0, 1)),
    )
    private val crop = CropRect(0, 0, 100, 200)
    private val framing = Framing(crop)

    /**
     * The crop and the landscape window derived from it (200 x 100, slid inside the frame) make a square of
     * 200: the export, whose left half is the crop.
     */
    private fun exported(thumbnailPath: String = FakeMediaStorage.THUMBNAIL, tint: Tint? = null) =
        NewWallpaper.ProcessedVideo(FakeMediaStorage.PROCESSED, thumbnailPath, tint, 200, 200, CropRect(0, 0, 100, 200))

    @Test
    fun `an optimised video holds both ways the screen can be turned, its turned crop too`() = runTest(dispatcher) {
        val turned = CropRect(0, 1000, 200, 1100)

        useCase(video, Framing(crop, turned), clip, process = true, tint = null).toList()

        assertEquals(listOf(CropRect(0, 0, 200, 1100)), exportedAreas)
        assertEquals(
            NewWallpaper.ProcessedVideo(
                FakeMediaStorage.PROCESSED, FakeMediaStorage.THUMBNAIL, null, 200, 1100, crop, turned,
            ),
            added.single(),
        )
    }

    @Test
    fun `an encoder that can't take both ways gets the crop alone`() = runTest(dispatcher) {
        encodes = false

        useCase(video, framing, clip, process = true, tint = null).toList()

        assertEquals(listOf(crop), exportedAreas)
        assertEquals(
            NewWallpaper.ProcessedVideo(FakeMediaStorage.PROCESSED, FakeMediaStorage.THUMBNAIL, null, 100, 200, crop),
            added.single(),
        )
    }

    @Test
    fun `an original keeps its turned crop`() = runTest(dispatcher) {
        val turned = CropRect(0, 1000, 200, 1100)

        useCase(video, Framing(crop, turned), clip, process = false, tint = null).toList()

        assertEquals(
            NewWallpaper.Media(
                MediaType.VIDEO, FakeMediaStorage.MEDIA, FakeMediaStorage.THUMBNAIL, crop, clip, video.mediaInfo,
                turnedCrop = turned,
            ),
            added.single(),
        )
    }
    private val clip = ClipRange(1_000, 5_000)

    @Test
    fun `video without processing keeps the original and is cropped later in GL`() = runTest(dispatcher) {
        val statuses = useCase(video, framing, clip, process = false, tint = null).toList()

        assertEquals(listOf<ExportStatus>(ExportStatus.Success), statuses)
        assertTrue(exports.isEmpty())
        assertEquals(
            NewWallpaper.Media(MediaType.VIDEO, FakeMediaStorage.MEDIA, FakeMediaStorage.THUMBNAIL, crop, clip, video.mediaInfo),
            added.single(),
        )
    }

    @Test
    fun `processed video is exported in scratch and keeps only the export`() = runTest(dispatcher) {
        val statuses = useCase(video, framing, clip, process = true, tint = null).toList()

        assertEquals(listOf(ExportStatus.InProgress(50), ExportStatus.Success), statuses)
        assertEquals(listOf("editor/media" to "editor/processed.mp4"), exports)
        assertEquals(exported(), added.single())
        assertEquals(listOf(FakeMediaStorage.THUMBNAIL, FakeMediaStorage.PROCESSED), storage.persisted)
    }

    @Test
    fun `a save the export has finished runs to its end when the editor stops waiting for it`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        addGate = gate
        val saving = launch { useCase(video, framing, clip, process = true, tint = null).toList() }

        // The export is done and the wallpaper is being added when the user cancels.
        saving.cancel()
        gate.complete(Unit)
        saving.join()

        assertEquals(exported(), added.single())
        assertEquals(listOf(NEW_ID), activated)
        assertEquals(listOf(FakeMediaStorage.THUMBNAIL, FakeMediaStorage.PROCESSED), storage.persisted)
        assertTrue(storage.deleted.none { it in storage.persisted })
    }

    @Test
    fun `a tint is saved with the original, which is drawn under it later`() = runTest(dispatcher) {
        val tint = Tint(0x4D000000)

        useCase(video, framing, clip, process = false, tint = tint).toList()

        assertTrue(exports.isEmpty())
        assertEquals(tint, (added.single() as NewWallpaper.Media).tint)
    }

    @Test
    fun `a tint goes into a processed video's frames and is kept for its thumbnail`() = runTest(dispatcher) {
        val tint = Tint(0xCC3366FF.toInt())

        useCase(video, framing, clip, process = true, tint = tint).toList()

        assertEquals(listOf<Tint?>(tint), exportedTints)
        assertEquals(exported(tint = tint), added.single())
    }

    @Test
    fun `an edit saves the new tint in place of the old one`() = runTest(dispatcher) {
        val tinted = savedVideo.copy(edited = savedVideo.edited?.copy(tint = Tint(0x4D000000)))

        useCase(tinted, framing, clip, process = false, tint = null).toList()

        assertEquals(null, (updated.single().second as NewWallpaper.Media).tint)
    }

    @Test
    fun `failed export saves nothing and leaves nothing in permanent storage`() = runTest(dispatcher) {
        exportResult = listOf(ExportStatus.Error(IllegalStateException("encoder")))

        useCase(video, framing, clip, process = true, tint = null).toList()

        assertTrue(added.isEmpty())
        assertTrue(storage.persisted.isEmpty())
    }

    @Test
    fun `a wallpaper that can't be saved takes its copies with it`() = runTest(dispatcher) {
        saveError = IllegalStateException("database")

        assertFailsWith<IllegalStateException> { useCase(video, framing, clip, process = false, tint = null).toList() }

        assertEquals(listOf(FakeMediaStorage.THUMBNAIL, FakeMediaStorage.MEDIA), storage.persisted)
        assertEquals(storage.persisted, storage.deleted)
    }

    @Test
    fun `image is never processed and has no video thumbnail`() = runTest(dispatcher) {
        val image = PreparedMedia("editor/media", MediaType.IMAGE, MediaInfo(800, 600, 0, 0))

        useCase(image, framing, clip, process = true, tint = null).toList()

        assertTrue(exports.isEmpty())
        assertEquals(NewWallpaper.Media(MediaType.IMAGE, FakeMediaStorage.MEDIA, "", crop, clip, image.mediaInfo), added.single())
    }

    @Test
    fun `a new wallpaper becomes the active one, if the user wants it so`() = runTest(dispatcher) {
        useCase(video, framing, clip, process = false, tint = null).toList()
        assertEquals(listOf(NEW_ID), activated)

        appSettings.value = AppSettings(activateNewWallpapers = false)
        useCase(video, framing, clip, process = false, tint = null).toList()
        assertEquals(listOf(NEW_ID), activated)
    }

    @Test
    fun `an edit doesn't change which wallpaper is active`() = runTest(dispatcher) {
        useCase(savedVideo, framing, clip, process = false, tint = null).toList()

        assertTrue(activated.isEmpty())
    }

    @Test
    fun `an edit without processing only changes the crop and the trim`() = runTest(dispatcher) {
        useCase(savedVideo, framing, clip, process = false, tint = null).toList()

        assertEquals(
            7 to NewWallpaper.Media(MediaType.VIDEO, "originals/a", "thumbnails/old", crop, clip, video.mediaInfo),
            updated.single(),
        )
        assertTrue(storage.persisted.isEmpty())
        assertTrue(added.isEmpty())
    }

    @Test
    fun `an edit with processing exports the original and replaces it with the export`() = runTest(dispatcher) {
        useCase(savedVideo, framing, clip, process = true, tint = null).toList()

        assertEquals(listOf("originals/a" to "editor/processed.mp4"), exports)
        assertEquals(7 to exported(thumbnailPath = "thumbnails/old"), updated.single())
        assertEquals(listOf(FakeMediaStorage.PROCESSED), storage.persisted)
    }

    private companion object {
        const val NEW_ID = 42
    }
}
