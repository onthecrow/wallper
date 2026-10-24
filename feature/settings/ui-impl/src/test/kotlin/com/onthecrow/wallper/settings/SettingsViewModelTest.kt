package com.onthecrow.wallper.settings

import android.content.Intent
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.settings.domain.CreateReportUseCase
import com.onthecrow.wallper.settings.domain.ThemeMode
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpaperStorageUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.domain.model.WallpaperStorage
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val controller = FakeLiveWallpaperController()
    private val navigator = FakeNavigator()
    private val appSettings = MutableStateFlow(AppSettings())
    private val storage = MutableStateFlow<List<WallpaperStorage>>(emptyList())
    private var reportResult = Result.success("cache/reports/wallper-report.txt")
    private var reports = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = SettingsViewModel(
        reducer = SettingsReducer(),
        liveWallpaperController = controller,
        appSettings = object : AppSettingsSource {
            override val settings = appSettings
            override fun update(transform: (AppSettings) -> AppSettings) = appSettings.update(transform)
        },
        observeWallpaperStorageUseCase = object : ObserveWallpaperStorageUseCase {
            override fun invoke(): Flow<List<WallpaperStorage>> = storage
        },
        createReportUseCase = object : CreateReportUseCase {
            override suspend fun invoke(): String = reportResult.getOrThrow().also { reports++ }
        },
        navigator = navigator,
        dispatchersProvider = TestDispatchers(dispatcher),
    )

    @Test
    fun `settings come from the store and changes go back to it`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(SettingsEvent.OnSaveBatteryChange(false))
        viewModel.onEvent(SettingsEvent.OnScrollWithPagesChange(false))
        viewModel.onEvent(SettingsEvent.OnActivateNewWallpapersChange(false))
        viewModel.onEvent(SettingsEvent.OnShowBuiltInWallpapersChange(false))
        viewModel.onEvent(SettingsEvent.OnDynamicColorChange(false))
        viewModel.onEvent(SettingsEvent.OnPlaybackSelect(VideoPlayback.ONCE))
        viewModel.onEvent(SettingsEvent.OnThemeSelect(ThemeMode.DARK))

        val expected = AppSettings(
            videoPlayback = VideoPlayback.ONCE,
            pauseVideoToSaveBattery = false,
            scrollVideoWithPages = false,
            activateNewWallpapers = false,
            showBuiltInWallpapers = false,
            theme = ThemeMode.DARK,
            dynamicColor = false,
        )
        assertEquals(expected, appSettings.value)
        assertEquals(expected, viewModel.state.value.settings)
    }

    @Test
    fun `storage shows what every wallpaper takes together`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(0, viewModel.state.value.storageBytes)

        storage.value = listOf(storageOf(id = 1, bytes = 100), storageOf(id = 2, bytes = 50))
        assertEquals(150, viewModel.state.value.storageBytes)

        viewModel.onEvent(SettingsEvent.OnStorageClick)
        assertEquals(listOf<Destination>(StorageDestination), navigator.destinations)
    }

    @Test
    fun `a report is shared once it is written`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.eventFlow.toList(events) }

        viewModel.onEvent(SettingsEvent.OnSendReportClick)

        assertTrue(SettingsEvent.ShareReport("cache/reports/wallper-report.txt") in events)
        assertEquals(false, viewModel.state.value.isReportInProgress)
        assertEquals(1, reports)
    }

    @Test
    fun `a report that can't be written is reported to the screen`() = runTest(dispatcher) {
        reportResult = Result.failure(IOException("disk full"))
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.eventFlow.toList(events) }

        viewModel.onEvent(SettingsEvent.OnSendReportClick)

        assertTrue(SettingsEvent.OnReportFailed in events)
        assertEquals(false, viewModel.state.value.isReportInProgress)
    }

    @Test
    fun `the wallpaper is checked as soon as the screen opens`() = runTest(dispatcher) {
        controller.isSet = true
        val viewModel = createViewModel()

        assertEquals(true, viewModel.state.value.isWallpaperSet)
        assertEquals(1, controller.checks)
    }

    @Test
    fun `the wallpaper is checked again whenever the screen returns`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(false, viewModel.state.value.isWallpaperSet)

        // Set in the system picker, then back to the screen.
        controller.isSet = true
        viewModel.onEvent(SettingsEvent.OnResume)
        assertEquals(true, viewModel.state.value.isWallpaperSet)
        assertEquals(2, controller.checks)
    }

    @Test
    fun `a new wallpaper while the screen is open is checked again`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        controller.isSet = true
        controller.changes.emit(Unit)
        assertEquals(true, viewModel.state.value.isWallpaperSet)

        controller.isSet = false
        controller.changes.emit(Unit)
        assertEquals(false, viewModel.state.value.isWallpaperSet)
    }

    @Test
    fun `back leaves the screen`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(SettingsEvent.OnBackClick)

        assertEquals(1, navigator.backs)
    }

    private class FakeLiveWallpaperController : LiveWallpaperController {
        var isSet = false
        var checks = 0
        val changes = MutableSharedFlow<Unit>()
        override fun createSetWallpaperIntent(): Intent = error("not used in these tests")
        override fun isSupported(wallpaper: BuiltInWallpaper) = true
        override fun isWallpaperSet(): Boolean {
            checks++
            return isSet
        }
        override fun wallpaperChanges(): Flow<Unit> = changes
    }

    private class FakeNavigator : Navigator {
        var backs = 0
        val destinations = mutableListOf<Destination>()
        override fun navigate(destination: Destination) { destinations += destination }
        override fun navigateBack() { backs++ }
    }

    private fun storageOf(id: Int, bytes: Long) = WallpaperStorage(
        wallpaper = Wallpaper.ProcessedVideo(id = id, path = "processed-$id", thumbnailPath = "thumbnail-$id", isActive = false),
        bytes = bytes,
    )

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatchersProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
}
