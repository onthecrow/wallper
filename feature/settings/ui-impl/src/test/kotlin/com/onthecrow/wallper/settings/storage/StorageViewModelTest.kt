package com.onthecrow.wallper.settings.storage

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.EditorDestination
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpaperStorageUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.domain.model.WallpaperStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val storage = MutableStateFlow<List<WallpaperStorage>?>(null)
    private val destinations = mutableListOf<Destination>()
    private var backs = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = StorageViewModel(
        reducer = StorageReducer(),
        observeWallpaperStorageUseCase = object : ObserveWallpaperStorageUseCase {
            override fun invoke(): Flow<List<WallpaperStorage>> = flow {
                storage.collect { list -> list?.let { emit(it) } }
            }
        },
        navigator = object : Navigator {
            override fun navigate(destination: Destination) { destinations += destination }
            override fun navigateBack() { backs++ }
        },
        dispatchersProvider = object : DispatchersProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        },
    )

    @Test
    fun `wallpapers are counted, the largest first, and only original videos can be optimised`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertNull(viewModel.state.value.items)

        storage.value = listOf(
            WallpaperStorage(media(id = 1, type = MediaType.IMAGE), bytes = 3_000),
            WallpaperStorage(media(id = 2, type = MediaType.VIDEO), bytes = 160_000),
            WallpaperStorage(Wallpaper.ProcessedVideo(3, "processed-3", "thumbnail-3", isActive = false), bytes = 12_000),
        )

        assertEquals(
            listOf(
                StorageItem(2, "thumbnail-2", StorageItem.Kind.ORIGINAL_VIDEO, 160_000),
                StorageItem(3, "thumbnail-3", StorageItem.Kind.OPTIMISED_VIDEO, 12_000),
                StorageItem(1, "original-1", StorageItem.Kind.PICTURE, 3_000),
            ),
            viewModel.state.value.items,
        )
        assertEquals(175_000, viewModel.state.value.totalBytes)
        assertEquals(listOf(true, false, false), viewModel.state.value.items?.map { it.canOptimise })
    }

    @Test
    fun `optimise opens the editor on the wallpaper, switched to optimised`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(StorageEvent.OnOptimiseClick(2))

        assertEquals(listOf<Destination>(EditorDestination.Edit(2, optimise = true)), destinations)
    }

    @Test
    fun `back leaves the screen`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(StorageEvent.OnBackClick)

        assertEquals(1, backs)
    }

    private fun media(id: Int, type: MediaType) = Wallpaper.Media(
        id = id,
        type = type,
        originalPath = "original-$id",
        thumbnailPath = "thumbnail-$id",
        crop = CropRect(0, 0, 10, 10),
        clip = ClipRange(0, 1_000),
        mediaInfo = MediaInfo(10, 10, 0, 1_000),
        isActive = false,
    )
}
