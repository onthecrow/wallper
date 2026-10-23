package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.model.PictureLayer
import com.onthecrow.wallper.blackhole.model.SettingsSlider
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualitySetting
import com.onthecrow.wallper.core.blackhole.physics.StarMath
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertSame

class BlackHoleSettingsReducerTest {

    private val reducer = BlackHoleSettingsReducer()

    @Test
    fun `switches and choices show up at once`() = runTest {
        var state = BlackHoleSettingsState()
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnQualityChange(QualitySetting.HIGH))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnGyroToggle(false))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnLauncherToggle(false))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnOrbitToggle(false))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnDopplerToggle(true))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnOpacityFalloffToggle(false))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnIdleFpsChange(24))

        assertEquals(
            BlackHoleSettings(
                quality = QualitySetting.HIGH,
                gyroEnabled = false,
                launcherEnabled = false,
                orbitEnabled = false,
                dopplerEnabled = true,
                diskOpacityFalloff = false,
                idleFps = 24,
            ),
            state.settings,
        )
    }

    @Test
    fun `a dragged slider moves its setting, clamped to the range`() = runTest {
        var state = reducer.reduce(BlackHoleSettingsState(), BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISTANCE, 41.5))
        assertEquals(41.5, state.settings.cameraDistance)

        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.ELEVATION, 90.0))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.ORBIT_PERIOD, 10.0))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DOPPLER_STRENGTH, 0.25))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISK_ROLL, -90.0))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISK_SPEED, 2.5))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.STAR_COUNT, 0.5))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.LENS_STRENGTH, 3.0))

        assertEquals(BlackHoleSettings.ROLL_RANGE.start, state.settings.diskRollDeg)
        assertEquals(2.5, state.settings.diskSpeed)
        assertEquals(0.5, state.settings.starCount)
        assertEquals(BlackHoleSettings.LENS_STRENGTH_RANGE.endInclusive, state.settings.lensStrength)
        assertEquals(BlackHoleSettings.ELEVATION_RANGE.endInclusive, state.settings.elevationDeg)
        assertEquals(BlackHoleSettings.ORBIT_RANGE.start, state.settings.orbitPeriodSec)
        assertEquals(0.25, state.settings.dopplerStrength)
    }

    @Test
    fun `the store replaces what is shown`() = runTest {
        val stored = BlackHoleSettings(starSeed = 42, cameraDistance = 50.0)
        val state = reducer.reduce(
            BlackHoleSettingsState(settings = BlackHoleSettings(cameraDistance = 25.0), isDebugAvailable = true),
            BlackHoleSettingsEvent.OnSettingsChanged(stored),
        )

        assertEquals(BlackHoleSettingsState(settings = stored, isDebugAvailable = true), state)
    }

    @Test
    fun `buttons and the end of a drag are side effects only`() = runTest {
        val initial = BlackHoleSettingsState()

        listOf(
            BlackHoleSettingsEvent.OnSliderChangeFinished(SettingsSlider.DISTANCE),
            BlackHoleSettingsEvent.OnShuffleStarsClick,
            BlackHoleSettingsEvent.OnResetClick,
            BlackHoleSettingsEvent.OnDebugClick,
        ).forEach { event -> assertSame(initial, reducer.reduce(initial, event)) }
    }

    @Test
    fun `layers, freezing and preview reports land in the state`() = runTest {
        var state = reducer.reduce(BlackHoleSettingsState(), BlackHoleSettingsEvent.OnLayerToggle(PictureLayer.HAZE, false))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnFreezeTimeToggle(true))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnFrameStats(fps = 59.0, gpuMs = 3.5))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnRenderFailure("lens.frag: error"))

        assertFalse(state.settings.hazeEnabled)
        assertFalse(state.params.haze.enabled)
        assertTrue(state.toPreviewConfig().isTimeFrozen)
        assertEquals(59.0, state.fps)
        assertEquals(3.5, state.gpuMs)
        assertEquals("lens.frag: error", state.renderFailure)
    }

    @Test
    fun `sharpness sets star size and widening together, the sliders fine-tune them`() = runTest {
        var state = reducer.reduce(BlackHoleSettingsState(), BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.STAR_SHARPNESS, 1.0))

        assertEquals(StarMath.sizePx(1.0), state.settings.starSizePx)
        assertEquals(StarMath.footprintWidening(1.0), state.settings.starWidening)
        assertEquals(1.0, SettingsSlider.STAR_SHARPNESS.value(state.settings), 1e-12)

        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.STAR_WIDENING, 0.2))
        state = reducer.reduce(state, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.FOV, 70.0))

        assertEquals(0.2, state.settings.starWidening)
        assertEquals(StarMath.sizePx(1.0), state.settings.starSizePx)
        assertEquals(70.0, state.params.camera.fovYDeg)
    }

    @Test
    fun `the sheet opens and closes`() = runTest {
        val closed = reducer.reduce(BlackHoleSettingsState(), BlackHoleSettingsEvent.OnPanelToggle)

        assertFalse(closed.isPanelExpanded)
        assertTrue(reducer.reduce(closed, BlackHoleSettingsEvent.OnPanelToggle).isPanelExpanded)
    }

    @Test
    fun `advanced settings start closed and open on demand`() = runTest {
        val initial = BlackHoleSettingsState()
        assertFalse(initial.isAdvancedExpanded)

        val open = reducer.reduce(initial, BlackHoleSettingsEvent.OnAdvancedToggle)

        assertTrue(open.isAdvancedExpanded)
        assertFalse(reducer.reduce(open, BlackHoleSettingsEvent.OnAdvancedToggle).isAdvancedExpanded)
    }

    @Test
    fun `params and the wanted tables follow the settings`() = runTest {
        val initial = BlackHoleSettingsState()

        val moved = reducer.reduce(initial, BlackHoleSettingsEvent.OnSliderChange(SettingsSlider.DISTANCE, 40.0))

        assertEquals(40.0, moved.params.camera.distance)
        assertEquals(40.0, moved.wantedTablesKey.cameraDistance)
        assertEquals(moved.settings.previewTablesKey(), moved.wantedTablesKey)
        // The store echoing what is shown changes nothing.
        assertSame(moved, reducer.reduce(moved, BlackHoleSettingsEvent.OnSettingsChanged(moved.settings)))
    }

    @Test
    fun `tables for another key than the wanted one are dropped`() = runTest {
        val initial = BlackHoleSettingsState()
        val stale = initial.wantedTablesKey.copy(cameraDistance = 50.0)
        val tables = LensTables(LensTableKey(50.0, 1, 1, 7.0), FloatArray(2), FloatArray(2))

        assertSame(initial, reducer.reduce(initial, BlackHoleSettingsEvent.OnTablesLoaded(stale, tables)))
        assertFalse(reducer.reduce(initial, BlackHoleSettingsEvent.OnTablesLoaded(initial.wantedTablesKey, tables)).isTablesLoading)
    }
}
