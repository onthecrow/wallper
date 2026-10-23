package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.model.DebugSlider
import com.onthecrow.wallper.blackhole.model.DebugToggle
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.physics.QualitySetting
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlackHoleDebugViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeSettingsSource(BlackHoleSettings(quality = QualitySetting.MEDIUM, cameraDistance = 30.0))
    private val tables = FakeLensTablesSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = BlackHoleDebugViewModel(
        reducer = BlackHoleDebugReducer(),
        settingsSource = settings,
        tablesSource = tables,
    )

    @Test
    fun `tables for the stored settings load on start`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val key = viewModel.state.value.wantedTablesKey

        assertEquals(listOf(key), tables.requests)
        assertEquals(30.0, key.cameraDistance)
        assertEquals(QualityLevel.MEDIUM.tableAlpha, key.nAlpha)
        assertTrue(viewModel.state.value.isTablesLoading)

        val loaded = tables.complete(key)

        assertSame(loaded, viewModel.state.value.tables)
        assertFalse(viewModel.state.value.isTablesLoading)
    }

    @Test
    fun `the stored quality decides the tables`() = runTest(dispatcher) {
        settings.settingsFlow.value = BlackHoleSettings(quality = QualitySetting.LOW)

        val viewModel = createViewModel()

        assertEquals(QualityLevel.LOW.tableAlpha, tables.requests.single().nAlpha)
        assertEquals(QualityLevel.LOW, viewModel.state.value.quality)
    }

    @Test
    fun `a quality change asks for its tables and drops the old load`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val mediumKey = viewModel.state.value.wantedTablesKey

        viewModel.onEvent(BlackHoleDebugEvent.OnQualityChange(QualityLevel.LOW))
        val lowKey = viewModel.state.value.wantedTablesKey

        assertEquals(listOf(mediumKey, lowKey), tables.requests)
        assertTrue(tables.isCancelled(mediumKey))
        assertSame(tables.complete(lowKey), viewModel.state.value.tables)
    }

    @Test
    fun `a newer distance cancels the load of the older one`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val first = viewModel.state.value.wantedTablesKey

        viewModel.onEvent(BlackHoleDebugEvent.OnDistanceChange(50.0))
        viewModel.onEvent(BlackHoleDebugEvent.OnDistanceChangeFinished)

        assertTrue(tables.isCancelled(first))
        val second = tables.complete(viewModel.state.value.wantedTablesKey)
        assertSame(second, viewModel.state.value.tables)
    }

    @Test
    fun `the distance asks for tables once, when the drag ends`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        tables.complete(viewModel.state.value.wantedTablesKey)

        listOf(35.0, 40.0, 42.0).forEach { viewModel.onEvent(BlackHoleDebugEvent.OnDistanceChange(it)) }

        assertEquals(1, tables.requests.size)
        assertEquals(42.0, viewModel.state.value.distance)
        assertFalse(viewModel.state.value.isTablesLoading)

        viewModel.onEvent(BlackHoleDebugEvent.OnDistanceChangeFinished)

        assertEquals(2, tables.requests.size)
        assertEquals(42.0, tables.requests.last().cameraDistance)
        assertEquals(42.0, viewModel.state.value.params.camera.distance)
        assertTrue(viewModel.state.value.isTablesLoading)
        // The old tables stay on screen meanwhile.
        assertEquals(30.0, viewModel.state.value.tablesKey?.cameraDistance)
    }

    @Test
    fun `parameter edits fold into the state without new tables or writes to the store`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(BlackHoleDebugEvent.OnSliderChange(DebugSlider.FOV, 40.0))
        viewModel.onEvent(BlackHoleDebugEvent.OnSliderChange(DebugSlider.LENS_STRENGTH, 0.5))
        viewModel.onEvent(BlackHoleDebugEvent.OnToggle(DebugToggle.STARS, false))
        viewModel.onEvent(BlackHoleDebugEvent.OnManualCameraToggle(true))

        val params = viewModel.state.value.params
        assertEquals(40.0, params.camera.fovYDeg)
        assertEquals(0.5, params.lens.strength)
        assertFalse(params.stars.enabled)
        assertTrue(viewModel.state.value.isManualCamera)
        assertEquals(1, tables.requests.size)
        assertEquals(0, settings.writes)
    }

    @Test
    fun `a failed load is reported and releasing the distance slider retries it`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val key = viewModel.state.value.wantedTablesKey

        tables.fail(key, IllegalStateException("out of memory"))

        assertEquals("out of memory", viewModel.state.value.tablesError)
        assertFalse(viewModel.state.value.isTablesLoading)

        viewModel.onEvent(BlackHoleDebugEvent.OnDistanceChange(key.cameraDistance))
        viewModel.onEvent(BlackHoleDebugEvent.OnDistanceChangeFinished)

        assertEquals(listOf(key, key), tables.requests)
        assertNull(viewModel.state.value.tablesError)
        assertTrue(viewModel.state.value.isTablesLoading)
    }

    private class FakeSettingsSource(initial: BlackHoleSettings) : BlackHoleSettingsSource {
        val settingsFlow = MutableStateFlow(initial)
        override val settings: StateFlow<BlackHoleSettings> = settingsFlow
        var writes = 0
            private set

        override fun update(transform: (BlackHoleSettings) -> BlackHoleSettings) {
            writes++
        }
    }

}
