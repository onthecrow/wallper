package com.onthecrow.wallper.wallpapers

import android.content.Intent
import com.onthecrow.wallper.blackhole.BlackHoleSettingsDestination
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.BuiltInPreviews
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.EditorDestination
import com.onthecrow.wallper.editor.domain.IsMediaSupportedUseCase
import com.onthecrow.wallper.settings.SettingsDestination
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.DeleteWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.MakeSeriesPictureUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpapersUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val wallpapers = MutableStateFlow<List<Wallpaper>>(listOf(wallpaper(id = 1, type = MediaType.VIDEO)))
    private val activated = mutableListOf<Int>()
    private val deleted = mutableListOf<Int>()
    private val navigator = FakeNavigator()
    private val previews = FakeBuiltInPreviews()
    private val seriesPictures = FakeSeriesPictures()
    private var mediaSupported = true
    private var builtInsSupported = true
    private val appSettings = MutableStateFlow(AppSettings())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = WallpapersViewModel(
        reducer = WallpapersReducer(),
        observeWallpapersUseCase = object : ObserveWallpapersUseCase {
            override fun invoke(): Flow<List<Wallpaper>> = wallpapers
        },
        activateWallpaperUseCase = object : ActivateWallpaperUseCase {
            override suspend fun invoke(id: Int) { activated += id }
        },
        deleteWallpaperUseCase = object : DeleteWallpaperUseCase {
            override suspend fun invoke(id: Int) { deleted += id }
        },
        isMediaSupportedUseCase = object : IsMediaSupportedUseCase {
            override suspend fun invoke(uri: String) = mediaSupported
        },
        liveWallpaperController = object : LiveWallpaperController {
            override fun createSetWallpaperIntent(): Intent = error("not used in these tests")
            override fun isSupported(wallpaper: BuiltInWallpaper) = builtInsSupported
            override fun isWallpaperSet(): Boolean = error("not used in these tests")
            override fun wallpaperChanges(): Flow<Unit> = error("not used in these tests")
        },
        builtInPreviews = previews,
        makeSeriesPictureUseCase = seriesPictures,
        appSettings = object : AppSettingsSource {
            override val settings = appSettings
            override fun update(transform: (AppSettings) -> AppSettings) = appSettings.update(transform)
        },
        navigator = navigator,
        dispatchersProvider = TestDispatchers(dispatcher),
    )

    @Test
    fun `a series of one shows its member's picture and edits open its members`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        wallpapers.value = listOf(group(id = 7, wallpaper(id = 1, type = MediaType.VIDEO)))
        viewModel.onEvent(WallpapersEvent.OnGroupEditClick(7))

        assertEquals(
            listOf(WallpaperItem(7, "thumbnail-1", true, isGroup = true, cover = listOf(WallpaperItem(1, "thumbnail-1", false)))),
            viewModel.state.value.items,
        )
        assertEquals(listOf(7 to listOf<String?>("thumbnail-1")), seriesPictures.requests)
        assertEquals(listOf<Destination>(WallpaperGroupDestination(7)), navigator.destinations)
    }

    @Test
    fun `a series' picture is made of its first four members' once the built-ins' previews are in`() = runTest(dispatcher) {
        wallpapers.value = listOf(group(id = 7, builtIn(id = 5), *(1..4).map { wallpaper(it, MediaType.VIDEO) }.toTypedArray()))
        val viewModel = createViewModel()

        assertTrue(seriesPictures.requests.isEmpty())
        assertEquals(null, viewModel.state.value.items.single().picturePath)

        previews.paths.emit("previews/black_hole_1.png")
        assertEquals(
            listOf(7 to listOf<String?>("previews/black_hole_1.png", "thumbnail-1", "thumbnail-2", "thumbnail-3")),
            seriesPictures.requests,
        )
        assertEquals("series-7", viewModel.state.value.items.single().picturePath)

        // Made again only when what it shows changes.
        wallpapers.value = listOf(group(id = 7, builtIn(id = 5), *(1..4).map { wallpaper(it, MediaType.VIDEO) }.toTypedArray(), isActive = false))
        assertEquals(1, seriesPictures.requests.size)
        previews.paths.emit("previews/black_hole_2.png")
        assertEquals(listOf("previews/black_hole_2.png", "thumbnail-1", "thumbnail-2", "thumbnail-3"), seriesPictures.requests.last().second)
    }

    @Test
    fun `a built-in this device can't draw leaves its part of a series' picture empty`() = runTest(dispatcher) {
        builtInsSupported = false
        wallpapers.value = listOf(group(id = 7, builtIn(id = 5), wallpaper(id = 1, type = MediaType.VIDEO)))

        createViewModel()

        assertEquals(listOf(7 to listOf(null, "thumbnail-1")), seriesPictures.requests)
        assertEquals(emptyList(), previews.requests)
    }

    @Test
    fun `a new series opens an empty group`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnAddClick)
        viewModel.onEvent(WallpapersEvent.OnAddSeriesClick)

        assertEquals(listOf<Destination>(WallpaperGroupDestination()), navigator.destinations)
        assertEquals(false, viewModel.state.value.isAddDialogShown)
    }

    @Test
    fun `wallpapers are shown with the thumbnail for videos`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertEquals(listOf(WallpaperItem(1, "thumbnail-1", false)), viewModel.state.value.items)

        wallpapers.value = listOf(wallpaper(id = 2, type = MediaType.IMAGE))
        assertEquals(listOf(WallpaperItem(2, "original-2", false)), viewModel.state.value.items)
    }

    @Test
    fun `an optimised video shows its thumbnail and can't be edited`() = runTest(dispatcher) {
        wallpapers.value = listOf(Wallpaper.ProcessedVideo(id = 3, path = "processed-3", thumbnailPath = "thumbnail-3", isActive = true))

        val viewModel = createViewModel()

        assertEquals(listOf(WallpaperItem(3, "thumbnail-3", true, isOptimised = true)), viewModel.state.value.items)
    }

    @Test
    fun `edit opens the editor on the saved wallpaper`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnEditClick(1))

        assertEquals(listOf<Destination>(EditorDestination.Edit(1)), navigator.destinations)
    }

    @Test
    fun `click activates`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnWallpaperClick(1))

        assertEquals(listOf(1), activated)
    }

    @Test
    fun `delete asks first and removes only once confirmed`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnDeleteClick(1))
        assertEquals(1, viewModel.state.value.deleteCandidate)
        assertEquals(emptyList(), deleted)

        viewModel.onEvent(WallpapersEvent.OnDeleteConfirm(1))
        assertEquals(listOf(1), deleted)
        assertEquals(null, viewModel.state.value.deleteCandidate)
    }

    @Test
    fun `a dismissed delete removes nothing`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnDeleteClick(1))
        viewModel.onEvent(WallpapersEvent.OnDeleteDismiss)

        assertEquals(emptyList(), deleted)
        assertEquals(null, viewModel.state.value.deleteCandidate)
    }

    @Test
    fun `a built-in shows a placeholder until its preview is rendered, then follows new ones`() = runTest(dispatcher) {
        wallpapers.value = listOf(builtIn(id = 5), wallpaper(id = 1, type = MediaType.VIDEO))
        val viewModel = createViewModel()

        assertEquals(
            listOf(
                WallpaperItem(5, null, false, BuiltInWallpaper.BLACK_HOLE),
                WallpaperItem(1, "thumbnail-1", false),
            ),
            viewModel.state.value.items,
        )

        previews.paths.emit("previews/black_hole_1.png")
        assertEquals("previews/black_hole_1.png", viewModel.state.value.items.first().picturePath)

        wallpapers.value = listOf(builtIn(id = 5, isActive = true), wallpaper(id = 1, type = MediaType.VIDEO))
        assertEquals(
            WallpaperItem(5, "previews/black_hole_1.png", true, BuiltInWallpaper.BLACK_HOLE),
            viewModel.state.value.items.first(),
        )

        // Its settings changed the picture, e.g. on the settings screen above the list.
        previews.paths.emit("previews/black_hole_2.png")
        assertEquals("previews/black_hole_2.png", viewModel.state.value.items.first().picturePath)
        assertEquals(listOf(BuiltInWallpaper.BLACK_HOLE), previews.requests)
    }

    @Test
    fun `a preview that can't be rendered leaves the placeholder`() = runTest(dispatcher) {
        wallpapers.value = listOf(builtIn(id = 5))
        val viewModel = createViewModel()

        assertEquals(listOf(WallpaperItem(5, null, false, BuiltInWallpaper.BLACK_HOLE)), viewModel.state.value.items)
    }

    @Test
    fun `a built-in this device can't draw is hidden`() = runTest(dispatcher) {
        builtInsSupported = false
        wallpapers.value = listOf(builtIn(id = 5), wallpaper(id = 1, type = MediaType.VIDEO))
        val viewModel = createViewModel()

        assertEquals(listOf(WallpaperItem(1, "thumbnail-1", false)), viewModel.state.value.items)
        assertEquals(emptyList(), previews.requests)
    }

    @Test
    fun `hidden built-ins leave the list, unless one is the active wallpaper`() = runTest(dispatcher) {
        wallpapers.value = listOf(builtIn(id = 5), wallpaper(id = 1, type = MediaType.VIDEO))
        val viewModel = createViewModel()

        appSettings.value = AppSettings(showBuiltInWallpapers = false)
        assertEquals(listOf(1), viewModel.state.value.items.map { it.id })

        wallpapers.value = listOf(builtIn(id = 5, isActive = true), wallpaper(id = 1, type = MediaType.VIDEO))
        assertEquals(listOf(5, 1), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun `settings of a built-in open its settings screen`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnBuiltInSettingsClick(BuiltInWallpaper.BLACK_HOLE))

        assertEquals(listOf<Destination>(BlackHoleSettingsDestination), navigator.destinations)
    }

    @Test
    fun `the settings button opens the settings screen`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnSettingsClick)

        assertEquals(listOf<Destination>(SettingsDestination), navigator.destinations)
    }

    @Test
    fun `supported media opens the editor`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(WallpapersEvent.OnMediaPicked("content://video"))

        assertEquals(listOf<Destination>(EditorDestination.New("content://video")), navigator.destinations)
    }

    @Test
    fun `unsupported media is reported to the screen`() = runTest(dispatcher) {
        mediaSupported = false
        val viewModel = createViewModel()
        val events = mutableListOf<WallpapersEvent>()
        backgroundScope.launch { viewModel.eventFlow.toList(events) }

        viewModel.onEvent(WallpapersEvent.OnMediaPicked("content://file"))

        assertTrue(navigator.destinations.isEmpty())
        assertTrue(WallpapersEvent.OnUnsupportedMediaPicked in events)
    }

    private fun wallpaper(id: Int, type: MediaType) = Wallpaper.Media(
        id = id,
        type = type,
        originalPath = "original-$id",
        thumbnailPath = "thumbnail-$id",
        crop = CropRect(0, 0, 10, 10),
        clip = ClipRange(0, 1000),
        mediaInfo = MediaInfo(10, 10, 0, 1000),
        isActive = false,
    )

    private fun builtIn(id: Int, isActive: Boolean = false) =
        Wallpaper.BuiltIn(id = id, wallpaper = BuiltInWallpaper.BLACK_HOLE, isActive = isActive)

    private fun group(id: Int, vararg members: Wallpaper, isActive: Boolean = true) =
        Wallpaper.Group(id = id, members = members.toList(), slideDuration = SlideDuration.DEFAULT, isActive = isActive)

    /** A series of one shows its member's picture, as the real one does; others get `series-<id>`. */
    private class FakeSeriesPictures : MakeSeriesPictureUseCase {
        val requests = mutableListOf<Pair<Int, List<String?>>>()
        override suspend fun invoke(groupId: Int, pictures: List<String?>): String? {
            requests += groupId to pictures
            return if (pictures.size == 1) pictures.single() else "series-$groupId"
        }
    }

    /** Renders nothing until the test sends a path to [paths]. */
    private class FakeBuiltInPreviews : BuiltInPreviews {
        val paths = MutableSharedFlow<String>()
        val requests = mutableListOf<BuiltInWallpaper>()
        override fun previews(wallpaper: BuiltInWallpaper): Flow<String> {
            requests += wallpaper
            return paths
        }
    }

    private class FakeNavigator : Navigator {
        val destinations = mutableListOf<Destination>()
        override fun navigate(destination: Destination) { destinations += destination }
        override fun navigateBack() = Unit
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatchersProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
}
