package com.onthecrow.wallper.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.domain.PrepareSavedMediaUseCase
import com.onthecrow.wallper.editor.domain.ProcessingInfoMemory
import com.onthecrow.wallper.editor.domain.SaveWallpaperUseCase
import com.onthecrow.wallper.editor.domain.model.EditedWallpaper
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private data class SaveCall(val media: PreparedMedia, val crop: CropRect, val clip: ClipRange, val process: Boolean)

    private val dispatcher = UnconfinedTestDispatcher()
    private val media = PreparedMedia("temp/media", MediaType.VIDEO, MediaInfo(1920, 1080, 0, 10_000))
    private val savedMedia = PreparedMedia(
        path = "originals/a",
        type = MediaType.VIDEO,
        mediaInfo = MediaInfo(1920, 1080, 0, 10_000),
        edited = EditedWallpaper(id = 4, thumbnailPath = "thumbnails/a", crop = CropRect(0, 0, 50, 100), clip = ClipRange(2_000, 6_000)),
    )
    private val crop = CropRect(0, 0, 100, 200)
    private var prepareResult: Result<PreparedMedia> = Result.success(media)
    private val preparedSaved = mutableListOf<Int>()
    private var exportStatuses: Flow<ExportStatus> = flowOf(ExportStatus.InProgress(10), ExportStatus.Success)
    private val saveCalls = mutableListOf<SaveCall>()
    private val memory = FakeProcessingInfoMemory()
    private var backNavigations = 0
    private var discards = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(destination: EditorDestination = EditorDestination.New("content://video")) = EditorViewModel(
        destination = destination,
        reducer = EditorReducer(),
        prepareMediaUseCase = object : PrepareMediaUseCase {
            override suspend fun invoke(uri: String) = prepareResult
        },
        prepareSavedMediaUseCase = object : PrepareSavedMediaUseCase {
            override suspend fun invoke(wallpaperId: Int): Result<PreparedMedia> {
                preparedSaved += wallpaperId
                return Result.success(savedMedia)
            }
        },
        saveWallpaperUseCase = object : SaveWallpaperUseCase {
            override fun invoke(media: PreparedMedia, crop: CropRect, clip: ClipRange, process: Boolean): Flow<ExportStatus> {
                saveCalls += SaveCall(media, crop, clip, process)
                return exportStatuses
            }
        },
        discardPreparedMediaUseCase = { discards++ },
        processingInfoMemory = memory,
        navigator = object : Navigator {
            override fun navigate(destination: Destination) = Unit
            override fun navigateBack() { backNavigations++ }
        },
    )

    @Test
    fun `picked media is prepared on start`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertEquals(media, viewModel.state.value.media)
    }

    @Test
    fun `a saved wallpaper is opened by its id, with its trim`() = runTest(dispatcher) {
        val viewModel = createViewModel(EditorDestination.Edit(4))

        assertEquals(listOf(4), preparedSaved)
        assertEquals(savedMedia, viewModel.state.value.media)
        assertEquals(ClipRange(2_000, 6_000), viewModel.state.value.trim)
    }

    @Test
    fun `an edit opened to optimise starts optimised, once the explanation was seen`() = runTest(dispatcher) {
        memory.seen = true

        val viewModel = createViewModel(EditorDestination.Edit(4, optimise = true))

        assertEquals(true, viewModel.state.value.isOptimised)
        assertNull(viewModel.state.value.processingInfo)
    }

    @Test
    fun `an edit opened to optimise explains it first, the first time`() = runTest(dispatcher) {
        val viewModel = createViewModel(EditorDestination.Edit(4, optimise = true))

        assertEquals(false, viewModel.state.value.isOptimised)
        assertNotNull(viewModel.state.value.processingInfo)

        viewModel.onEvent(EditorEvent.OnProcessingInfoConfirm)
        assertEquals(true, viewModel.state.value.isOptimised)
    }

    @Test
    fun `failed preparation shows the error`() = runTest(dispatcher) {
        prepareResult = Result.failure(IllegalStateException("broken file"))

        val viewModel = createViewModel()

        assertIs<ExportStatus.Error>(viewModel.state.value.exportStatus)
    }

    @Test
    fun `dismissing a preparation error goes back, there is nothing to edit`() = runTest(dispatcher) {
        prepareResult = Result.failure(IllegalStateException("broken file"))
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnErrorDismiss)

        assertEquals(1, backNavigations)
    }

    @Test
    fun `a preview that can't play shows the error and goes back once it is closed`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnPreviewFailed(IllegalStateException("file gone")))

        assertIs<ExportStatus.Error>(viewModel.state.value.exportStatus)
        assertEquals(0, backNavigations)

        viewModel.onEvent(EditorEvent.OnErrorDismiss)

        assertEquals(1, backNavigations)
    }

    @Test
    fun `closing the editor discards its copy of the media`() = runTest(dispatcher) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel() as T
        }
        ViewModelProvider(store, factory)[EditorViewModel::class.java]

        assertEquals(0, discards)
        store.clear()

        assertEquals(1, discards)
    }

    @Test
    fun `save keeps the trimmed part and goes back on success`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEvent(EditorEvent.OnTrimChange(ClipRange(1_000, 5_000)))

        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertEquals(SaveCall(media, crop, ClipRange(1_000, 5_000), process = false), saveCalls.single())
        assertEquals(ExportStatus.Success, viewModel.state.value.exportStatus)
        assertEquals(1, backNavigations)
    }

    @Test
    fun `save without moving the handles keeps the whole video`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertEquals(ClipRange(0, 10_000), saveCalls.single().clip)
    }

    @Test
    fun `an edit saves over the wallpaper it opened`() = runTest(dispatcher) {
        val viewModel = createViewModel(EditorDestination.Edit(4))

        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertEquals(SaveCall(savedMedia, crop, ClipRange(2_000, 6_000), process = false), saveCalls.single())
        assertEquals(1, backNavigations)
    }

    @Test
    fun `the first switch to optimised waits for OK on the explanation, then saves optimised`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnOptimisedToggle)

        assertNotNull(viewModel.state.value.processingInfo)
        assertEquals(false, viewModel.state.value.isOptimised)
        assertEquals(1, memory.marks)

        viewModel.onEvent(EditorEvent.OnProcessingInfoConfirm)
        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertNull(viewModel.state.value.processingInfo)
        assertEquals(SaveCall(media, crop, ClipRange(0, 10_000), process = true), saveCalls.single())
        assertEquals(1, backNavigations)
    }

    @Test
    fun `dismissing the explanation stays original`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEvent(EditorEvent.OnOptimisedToggle)

        viewModel.onEvent(EditorEvent.OnProcessingInfoDismiss)
        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertNull(viewModel.state.value.processingInfo)
        assertEquals(false, saveCalls.single().process)
    }

    @Test
    fun `an explanation seen before doesn't hold the switch`() = runTest(dispatcher) {
        memory.seen = true
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnOptimisedToggle)

        assertNull(viewModel.state.value.processingInfo)
        assertEquals(true, viewModel.state.value.isOptimised)
        assertEquals(0, memory.marks)
    }

    @Test
    fun `the question mark counts as seen`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnProcessingInfoClick)
        viewModel.onEvent(EditorEvent.OnProcessingInfoConfirm)

        assertEquals(false, viewModel.state.value.isOptimised)
        assertEquals(1, memory.marks)

        viewModel.onEvent(EditorEvent.OnOptimisedToggle)
        assertNull(viewModel.state.value.processingInfo)
        assertEquals(true, viewModel.state.value.isOptimised)
        assertEquals(1, memory.marks)
    }

    @Test
    fun `failed export stays on the screen and can be saved again`() = runTest(dispatcher) {
        exportStatuses = flowOf(ExportStatus.Error(IllegalStateException("encoder")))
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertIs<ExportStatus.Error>(viewModel.state.value.exportStatus)
        assertEquals(0, backNavigations)

        viewModel.onEvent(EditorEvent.OnErrorDismiss)

        assertNull(viewModel.state.value.exportStatus)
        assertEquals(0, backNavigations)
        viewModel.onEvent(EditorEvent.OnSaveClick(crop))
        assertEquals(2, saveCalls.size)
    }

    @Test
    fun `a save that throws shows the error instead of hanging`() = runTest(dispatcher) {
        exportStatuses = flow {
            emit(ExportStatus.InProgress(10))
            throw IllegalStateException("database")
        }
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnSaveClick(crop))

        assertIs<ExportStatus.Error>(viewModel.state.value.exportStatus)
        assertNull(viewModel.state.value.save)
        assertEquals(0, backNavigations)
    }

    private class FakeProcessingInfoMemory : ProcessingInfoMemory {
        var seen = false
        var marks = 0

        override suspend fun isSeen() = seen

        override fun markSeen() {
            marks++
        }
    }
}
