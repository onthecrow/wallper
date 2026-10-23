package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.model.PictureLayer
import com.onthecrow.wallper.blackhole.model.SettingsSlider
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import com.onthecrow.wallper.core.blackhole.physics.QualitySetting
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlackHoleSettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val source = FakeSettingsSource(BlackHoleSettings(cameraDistance = 30.0, starSeed = 7))
    private val navigator = FakeNavigator()
    private val tables = FakeLensTablesSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(debuggable: Boolean = false) = BlackHoleSettingsViewModel(
        reducer = BlackHoleSettingsReducer(),
        settingsSource = source,
        tablesSource = tables,
        navigator = navigator,
        debuggableBuild = { debuggable },
    )

    @Test
    fun `the screen starts from the stored settings`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertEquals(source.settings.value, viewModel.state.value.settings)
    }

    @Test
    fun `switches and choices are stored right away`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnGyroToggle(false))
        assertEquals(1, source.writes)
        assertFalse(source.settings.value.gyroEnabled)

        viewModel.onEvent(BlackHoleSettingsEvent.OnQualityChange(QualitySetting.LOW))
        viewModel.onEvent(BlackHoleSettingsEvent.OnDopplerToggle(true))
        viewModel.onEvent(BlackHoleSettingsEvent.OnOpacityFalloffToggle(false))
        viewModel.onEvent(BlackHoleSettingsEvent.OnIdleFpsChange(24))

        assertEquals(5, source.writes)
        assertEquals(
            BlackHoleSettings(
                cameraDistance = 30.0,
                starSeed = 7,
                gyroEnabled = false,
                quality = QualitySetting.LOW,
                dopplerEnabled = true,
                diskOpacityFalloff = false,
                idleFps = 24,
            ),
            source.settings.value,
        )
        assertEquals(source.settings.value, viewModel.state.value.settings)
    }

    @Test
    fun `a slider is stored once, when the drag ends`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        listOf(31.0, 35.0, 40.0).forEach { distance ->
            viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISTANCE, distance))
        }

        assertEquals(40.0, viewModel.state.value.settings.cameraDistance)
        assertEquals(0, source.writes)
        assertEquals(30.0, source.settings.value.cameraDistance)

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.DISTANCE))

        assertEquals(1, source.writes)
        assertEquals(40.0, source.settings.value.cameraDistance)
        assertEquals(40.0, viewModel.state.value.settings.cameraDistance)
    }

    @Test
    fun `finishing one slider stores only its own setting`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISK_SPEED, 3.0))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.STAR_COUNT, 1.5))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.STAR_COUNT))

        assertEquals(1.5, source.settings.value.starCount)
        assertEquals(BlackHoleSettings().diskSpeed, source.settings.value.diskSpeed)
    }

    @Test
    fun `the preview loads tables for the stored settings and follows a new distance`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val first = viewModel.state.value.wantedTablesKey
        assertEquals(listOf(first), tables.requests)
        assertSame(tables.complete(first), viewModel.state.value.tables)

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISTANCE, 45.0))
        assertEquals(1, tables.requests.size) // not while dragging

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.DISTANCE))

        assertEquals(45.0, tables.requests.last().cameraDistance)
        assertTrue(viewModel.state.value.isTablesLoading)
        // The old tables stay in the preview meanwhile.
        assertEquals(first, viewModel.state.value.tablesKey)
    }

    @Test
    fun `picture edits that keep the tables don't reload them`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.EXPOSURE, 2.0))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.EXPOSURE))

        assertEquals(1, tables.requests.size)
        assertEquals(2.0, source.settings.value.exposure)
    }

    @Test
    fun `layers and zoom out are stored, freezing the preview is not`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnLayerToggle(PictureLayer.STARS, false))
        viewModel.onEvent(BlackHoleSettingsEvent.OnZoomOutToggle(false))
        val writes = source.writes
        viewModel.onEvent(BlackHoleSettingsEvent.OnFreezeTimeToggle(true))

        assertFalse(source.settings.value.starsEnabled)
        assertFalse(source.settings.value.zoomOutEnabled)
        assertEquals(writes, source.writes)
        assertTrue(viewModel.state.value.isTimeFrozen)
    }

    @Test
    fun `field of view, star size and widening are stored on release`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.FOV, 45.0))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.FOV))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.STAR_SIZE, 0.9))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.STAR_SIZE))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.STAR_WIDENING, 0.1))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.STAR_WIDENING))

        assertEquals(45.0, source.settings.value.fovDeg)
        assertEquals(0.9, source.settings.value.starSizePx)
        assertEquals(0.1, source.settings.value.starWidening)
        assertEquals(1, tables.requests.size) // none of them needs new tables
    }

    @Test
    fun `response and recoil sliders are stored on release`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.RESPONSE_SMOOTHNESS, 0.6))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.RESPONSE_SMOOTHNESS))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.RECOIL_STRENGTH, 2.0))
        viewModel.onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.RECOIL_STRENGTH))

        assertEquals(0.6, source.settings.value.responseSmoothness)
        assertEquals(2.0, source.settings.value.recoilStrength)
        assertEquals(1, tables.requests.size) // none of them needs new tables
    }

    @Test
    fun `reset stores the defaults`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnResetClick)

        assertEquals(BlackHoleSettings(), source.settings.value)
        assertEquals(BlackHoleSettings(), viewModel.state.value.settings)
    }

    @Test
    fun `shuffle stores a new star seed`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleSettingsEvent.OnShuffleStarsClick)
        val first = source.settings.value.starSeed
        viewModel.onEvent(BlackHoleSettingsEvent.OnShuffleStarsClick)

        assertNotEquals(7, first)
        assertNotEquals(first, source.settings.value.starSeed)
        assertTrue(source.settings.value.starSeed > 0)
        assertEquals(source.settings.value.starSeed, viewModel.state.value.settings.starSeed)
        // Nothing but the seed changes.
        assertEquals(BlackHoleSettings(cameraDistance = 30.0, starSeed = 7), source.settings.value.copy(starSeed = 7))
    }

    @Test
    fun `the debug renderer opens in debuggable builds`() = runTest(dispatcher) {
        val viewModel = createViewModel(debuggable = true)

        assertTrue(viewModel.state.value.isDebugAvailable)
        viewModel.onEvent(BlackHoleSettingsEvent.OnDebugClick)

        assertEquals(listOf<Destination>(BlackHoleDebugDestination), navigator.destinations)
    }

    @Test
    fun `release builds have no debug renderer`() = runTest(dispatcher) {
        val viewModel = createViewModel(debuggable = false)

        assertFalse(viewModel.state.value.isDebugAvailable)
        viewModel.onEvent(BlackHoleSettingsEvent.OnDebugClick)

        assertTrue(navigator.destinations.isEmpty())
    }

    private class FakeSettingsSource(initial: BlackHoleSettings) : BlackHoleSettingsSource {
        private val _settings = MutableStateFlow(initial)
        override val settings: StateFlow<BlackHoleSettings> = _settings
        var writes = 0
            private set

        override fun update(transform: (BlackHoleSettings) -> BlackHoleSettings) {
            writes++
            _settings.value = transform(_settings.value)
        }
    }

    private class FakeNavigator : Navigator {
        val destinations = mutableListOf<Destination>()
        override fun navigate(destination: Destination) { destinations += destination }
        override fun navigateBack() = Unit
    }
}
