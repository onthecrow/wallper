package com.onthecrow.wallper.wallpapers.group

import android.content.Intent
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
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.WallpaperGroupDestination
import com.onthecrow.wallper.wallpapers.domain.GetWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpapersUseCase
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperGroupUseCase
import com.onthecrow.wallper.wallpapers.domain.SwipeHintMemory
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
class GroupEditorViewModelTest {

    private data class SaveCall(val id: Int?, val memberIds: List<Int>, val slideDuration: SlideDuration)

    private val dispatcher = UnconfinedTestDispatcher()
    private val first = media(1)
    private val second = media(2)
    private val blackHole = Wallpaper.BuiltIn(3, BuiltInWallpaper.BLACK_HOLE, isActive = false)
    private val group = Wallpaper.Group(4, listOf(second, first), SlideDuration(30_000), isActive = true)
    private val wallpapers = MutableStateFlow<List<Wallpaper>>(listOf(blackHole, first, second, group))
    private val saves = mutableListOf<SaveCall>()
    private var saveError: Exception? = null
    private var backNavigations = 0
    private var builtInsSupported = true
    private val appSettings = MutableStateFlow(AppSettings())
    private var hintShown = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(groupId: Int? = null) = GroupEditorViewModel(
        destination = WallpaperGroupDestination(groupId),
        reducer = GroupEditorReducer(),
        observeWallpapersUseCase = object : ObserveWallpapersUseCase {
            override fun invoke(): Flow<List<Wallpaper>> = wallpapers
        },
        getWallpaperUseCase = object : GetWallpaperUseCase {
            override suspend fun invoke(id: Int) = wallpapers.value.firstOrNull { it.id == id }
        },
        saveWallpaperGroupUseCase = object : SaveWallpaperGroupUseCase {
            override suspend fun invoke(id: Int?, memberIds: List<Int>, slideDuration: SlideDuration): Int {
                saveError?.let { throw it }
                saves += SaveCall(id, memberIds, slideDuration)
                return id ?: 10
            }
        },
        liveWallpaperController = object : LiveWallpaperController {
            override fun createSetWallpaperIntent(): Intent = error("not used in these tests")
            override fun isSupported(wallpaper: BuiltInWallpaper) = builtInsSupported
            override fun isWallpaperSet(): Boolean = error("not used in these tests")
            override fun wallpaperChanges(): Flow<Unit> = error("not used in these tests")
        },
        builtInPreviews = object : BuiltInPreviews {
            override fun previews(wallpaper: BuiltInWallpaper): Flow<String> = emptyFlow()
        },
        swipeHintMemory = object : SwipeHintMemory {
            override suspend fun isDue() = hintShown < SwipeHintMemory.SHOWS
            override fun countShown() { hintShown++ }
        },
        appSettings = object : AppSettingsSource {
            override val settings = appSettings
            override fun update(transform: (AppSettings) -> AppSettings) = appSettings.update(transform)
        },
        navigator = object : Navigator {
            override fun navigate(destination: Destination) = Unit
            override fun navigateBack() { backNavigations++ }
        },
        dispatchersProvider = object : DispatchersProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        },
    )

    @Test
    fun `a new group offers every wallpaper but groups`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertEquals(listOf(3, 1, 2), viewModel.state.value.pickable.map { it.id })
        assertEquals(GroupEditorStep.PICK, viewModel.state.value.step)
    }

    @Test
    fun `a built-in this device can't draw isn't offered`() = runTest(dispatcher) {
        builtInsSupported = false

        val viewModel = createViewModel()

        assertEquals(listOf(1, 2), viewModel.state.value.pickable.map { it.id })
    }

    @Test
    fun `an edited group opens with its members and time`() = runTest(dispatcher) {
        val viewModel = createViewModel(groupId = 4)

        assertEquals(listOf(2, 1), viewModel.state.value.members)
        assertEquals(SlideDuration(30_000), viewModel.state.value.slideDuration)
        assertEquals(GroupEditorStep.ARRANGE, viewModel.state.value.step)
    }

    @Test
    fun `the swipe hint shows on the first visits of the members, each counted once`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        // Still picking: not a visit of the members yet.
        assertEquals(0, hintShown)

        viewModel.onEvent(GroupEditorEvent.OnWallpaperClick(1))
        viewModel.onEvent(GroupEditorEvent.OnPickConfirm)
        viewModel.onEvent(GroupEditorEvent.OnAddClick)
        viewModel.onEvent(GroupEditorEvent.OnPickCancel)
        assertEquals(true, viewModel.state.value.showsSwipeHint)
        assertEquals(1, hintShown)

        repeat(SwipeHintMemory.SHOWS - 1) { createViewModel(groupId = 4) }
        assertEquals(SwipeHintMemory.SHOWS, hintShown)

        val late = createViewModel(groupId = 4)
        assertEquals(false, late.state.value.showsSwipeHint)
        assertEquals(SwipeHintMemory.SHOWS, hintShown)
    }

    @Test
    fun `a group deleted meanwhile has nothing to edit`() = runTest(dispatcher) {
        createViewModel(groupId = 99)

        assertEquals(1, backNavigations)
    }

    @Test
    fun `a new group is saved with its members and time, then the screen closes`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(GroupEditorEvent.OnWallpaperClick(2))
        viewModel.onEvent(GroupEditorEvent.OnWallpaperClick(3))
        viewModel.onEvent(GroupEditorEvent.OnPickConfirm)
        viewModel.onEvent(GroupEditorEvent.OnDurationPick(SlideDuration(900_000)))
        viewModel.onEvent(GroupEditorEvent.OnSaveClick)

        assertEquals(listOf(SaveCall(null, listOf(3, 2), SlideDuration(900_000))), saves)
        assertEquals(1, backNavigations)
    }

    @Test
    fun `an edited group is saved in place`() = runTest(dispatcher) {
        val viewModel = createViewModel(groupId = 4)

        viewModel.onEvent(GroupEditorEvent.OnSaveClick)

        assertEquals(listOf(SaveCall(4, listOf(2, 1), SlideDuration(30_000))), saves)
    }

    @Test
    fun `a failed save is reported and can be tried again`() = runTest(dispatcher) {
        saveError = IllegalStateException("disk full")
        val viewModel = createViewModel(groupId = 4)
        val events = mutableListOf<GroupEditorEvent>()
        backgroundScope.launch { viewModel.eventFlow.toList(events) }

        viewModel.onEvent(GroupEditorEvent.OnSaveClick)

        assertTrue(GroupEditorEvent.OnSaveFailed in events)
        assertEquals(null, viewModel.state.value.save)
        assertEquals(0, backNavigations)

        saveError = null
        viewModel.onEvent(GroupEditorEvent.OnSaveClick)
        assertEquals(1, saves.size)
        assertEquals(1, backNavigations)
    }

    @Test
    fun `close leaves without saving`() = runTest(dispatcher) {
        val viewModel = createViewModel(groupId = 4)

        viewModel.onEvent(GroupEditorEvent.OnCloseClick)

        assertEquals(1, backNavigations)
        assertEquals(emptyList(), saves)
    }

    private fun media(id: Int) = Wallpaper.Media(
        id = id,
        type = MediaType.VIDEO,
        originalPath = "original-$id",
        thumbnailPath = "thumbnail-$id",
        crop = CropRect(0, 0, 10, 10),
        clip = ClipRange(0, 1000),
        mediaInfo = MediaInfo(10, 10, 0, 1000),
        isActive = false,
    )
}
