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
import com.onthecrow.wallper.editor.domain.CreateWallpaperUseCase
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val media = PreparedMedia("temp/media", MediaType.VIDEO, MediaInfo(1920, 1080, 0, 10_000))
    private var prepareResult: Result<PreparedMedia> = Result.success(media)
    private var exportStatuses: List<ExportStatus> = listOf(ExportStatus.InProgress(10), ExportStatus.Success)
    private val createCalls = mutableListOf<Triple<CropRect, ClipRange, Boolean>>()
    private var backNavigations = 0
    private var discards = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = EditorViewModel(
        destination = EditorDestination("content://video"),
        reducer = EditorReducer(),
        prepareMediaUseCase = object : PrepareMediaUseCase {
            override suspend fun invoke(uri: String) = prepareResult
        },
        createWallpaperUseCase = object : CreateWallpaperUseCase {
            override fun invoke(media: PreparedMedia, crop: CropRect, clip: ClipRange, preCrop: Boolean): Flow<ExportStatus> {
                createCalls += Triple(crop, clip, preCrop)
                return flowOf(*exportStatuses.toTypedArray())
            }
        },
        discardPreparedMediaUseCase = { discards++ },
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
    fun `save exports the trimmed part and goes back on success`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEvent(EditorEvent.OnTrimChange(ClipRange(1_000, 5_000)))
        viewModel.onEvent(EditorEvent.OnAdditionalProcessingToggle)

        viewModel.onEvent(EditorEvent.OnSaveClick(CropRect(0, 0, 100, 200)))

        assertEquals(Triple(CropRect(0, 0, 100, 200), ClipRange(1_000, 5_000), true), createCalls.single())
        assertEquals(ExportStatus.Success, viewModel.state.value.exportStatus)
        assertEquals(1, backNavigations)
    }

    @Test
    fun `save without moving the handles exports the whole video`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnSaveClick(CropRect(0, 0, 100, 200)))

        assertEquals(ClipRange(0, 10_000), createCalls.single().second)
    }

    @Test
    fun `failed export stays on the screen`() = runTest(dispatcher) {
        exportStatuses = listOf(ExportStatus.Error(IllegalStateException("encoder")))
        val viewModel = createViewModel()

        viewModel.onEvent(EditorEvent.OnSaveClick(CropRect(0, 0, 100, 200)))

        assertIs<ExportStatus.Error>(viewModel.state.value.exportStatus)
        assertEquals(0, backNavigations)

        viewModel.onEvent(EditorEvent.OnErrorDismiss)

        assertNull(viewModel.state.value.exportStatus)
        assertEquals(0, backNavigations)
        viewModel.onEvent(EditorEvent.OnSaveClick(CropRect(0, 0, 100, 200)))
        assertEquals(2, createCalls.size)
    }
}
