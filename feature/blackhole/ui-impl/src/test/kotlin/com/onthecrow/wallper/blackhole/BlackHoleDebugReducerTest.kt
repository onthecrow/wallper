package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.model.DebugSlider
import com.onthecrow.wallper.blackhole.model.DebugToggle
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.physics.LensTableKey
import com.onthecrow.wallper.blackhole.physics.LensTables
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.physics.QualitySetting
import com.onthecrow.wallper.blackhole.physics.lensTableKey
import com.onthecrow.wallper.blackhole.renderer.DebugOptions
import com.onthecrow.wallper.blackhole.renderer.DebugView
import com.onthecrow.wallper.blackhole.renderer.LensMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlackHoleDebugReducerTest {

    private val reducer = BlackHoleDebugReducer()
    private val initial = BlackHoleDebugState.initial(
        BlackHoleSettings(quality = QualitySetting.MEDIUM, cameraDistance = 30.0, elevationDeg = 10.0),
    )

    @Test
    fun `the screen starts from the stored settings, auto quality on high`() {
        assertEquals(30.0, initial.params.camera.distance)
        assertEquals(10.0, initial.params.camera.elevationDeg)
        assertEquals(10.0, initial.manualElevationDeg)
        assertEquals(QualityLevel.MEDIUM, initial.quality)
        assertEquals(QualityLevel.HIGH, BlackHoleDebugState.initial(BlackHoleSettings(quality = QualitySetting.AUTO)).quality)
        assertTrue(initial.isTablesLoading)
    }

    @Test
    fun `parameter sliders and toggles fold into the params`() = runTest {
        var state = reducer.reduce(initial, BlackHoleDebugEvent.OnSliderChange(DebugSlider.EXPOSURE, 2.0))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnSliderChange(DebugSlider.STAR_SIZE, 100.0))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnSliderChange(DebugSlider.DISK_ROTATION_PERIOD, 80.0))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnToggle(DebugToggle.BLOOM, false))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnToggle(DebugToggle.DISK, false))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnToggle(DebugToggle.OPACITY_FALLOFF, false))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnSliderChange(DebugSlider.DISK_OPACITY_FALLOFF, 2.0))

        assertEquals(2.0, state.params.post.exposure)
        assertEquals(DebugSlider.STAR_SIZE.range.endInclusive, state.params.stars.sizePx)
        assertEquals(80.0, state.params.disk.rotationPeriodSec)
        assertFalse(state.params.post.bloomEnabled)
        assertFalse(state.params.disk.enabled)
        assertFalse(state.params.disk.opacityFalloffEnabled)
        assertEquals(2.0, state.params.disk.opacityFalloffPower)
        // None of them needs new tables.
        assertEquals(initial.wantedTablesKey, state.wantedTablesKey)
    }

    @Test
    fun `quality changes the wanted tables`() = runTest {
        val state = reducer.reduce(initial, BlackHoleDebugEvent.OnQualityChange(QualityLevel.LOW))

        assertEquals(QualityLevel.LOW, state.quality)
        assertEquals(QualityLevel.LOW.tableAlpha, state.wantedTablesKey.nAlpha)
    }

    @Test
    fun `the field of view folds into the params, clamped, without new tables`() = runTest {
        var state = reducer.reduce(initial, BlackHoleDebugEvent.OnSliderChange(DebugSlider.FOV, 70.0))
        assertEquals(70.0, state.params.camera.fovYDeg)

        state = reducer.reduce(state, BlackHoleDebugEvent.OnSliderChange(DebugSlider.FOV, 500.0))

        assertEquals(DebugSlider.FOV.range.endInclusive, state.params.camera.fovYDeg)
        assertEquals(initial.wantedTablesKey, state.wantedTablesKey)
    }

    @Test
    fun `the distance reaches the params only when the drag ends`() = runTest {
        var state = reducer.reduce(initial, BlackHoleDebugEvent.OnDistanceChange(45.0))

        assertEquals(45.0, state.distance)
        assertEquals(30.0, state.params.camera.distance)
        assertEquals(initial.wantedTablesKey, state.wantedTablesKey)

        state = reducer.reduce(state, BlackHoleDebugEvent.OnDistanceChangeFinished)

        assertEquals(45.0, state.params.camera.distance)
        assertNull(state.draftDistance)
        assertEquals(lensTableKey(state.params, QualityLevel.MEDIUM), state.wantedTablesKey)
        assertEquals(45.0, state.wantedTablesKey.cameraDistance)
    }

    @Test
    fun `a release without a drag changes nothing`() = runTest {
        assertSame(initial, reducer.reduce(initial, BlackHoleDebugEvent.OnDistanceChangeFinished))
    }

    @Test
    fun `tables for the wanted key end the loading, late ones are dropped`() = runTest {
        val tables = tinyTables()
        val loaded = reducer.reduce(initial, BlackHoleDebugEvent.OnTablesLoaded(initial.wantedTablesKey, tables))

        assertSame(tables, loaded.tables)
        assertFalse(loaded.isTablesLoading)

        val stale = initial.wantedTablesKey.copy(cameraDistance = 50.0)
        assertSame(loaded, reducer.reduce(loaded, BlackHoleDebugEvent.OnTablesLoaded(stale, tinyTables())))
        assertSame(loaded, reducer.reduce(loaded, BlackHoleDebugEvent.OnTablesLoadFailed(stale, "boom")))
    }

    @Test
    fun `a failed load is shown until the next request`() = runTest {
        var state = reducer.reduce(initial, BlackHoleDebugEvent.OnTablesLoadFailed(initial.wantedTablesKey, "boom"))

        assertEquals("boom", state.tablesError)
        assertFalse(state.isTablesLoading)

        state = reducer.reduce(state, BlackHoleDebugEvent.OnDistanceChange(40.0))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnDistanceChangeFinished)

        assertNull(state.tablesError)
        assertTrue(state.isTablesLoading)
    }

    @Test
    fun `camera, time, modes and the panel`() = runTest {
        var state = reducer.reduce(initial, BlackHoleDebugEvent.OnManualCameraToggle(true))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnManualElevationChange(90.0))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnManualAzimuthChange(120.0))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnFreezeTimeToggle(true))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnLensModeChange(LensMode.DIFF))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnDebugViewChange(DebugView.FOOTPRINT))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnPanelToggle)

        assertEquals(BlackHoleSettings.ELEVATION_RANGE.endInclusive, state.manualElevationDeg)
        assertEquals(DebugOptions(LensMode.DIFF, DebugView.FOOTPRINT), state.debugOptions)
        assertFalse(state.isPanelExpanded)
        val config = state.toPreviewConfig()
        assertEquals(BlackHoleSettings.ELEVATION_RANGE.endInclusive, config.manualElevationDeg)
        assertEquals(120.0, config.manualAzimuthDeg)
        assertEquals(state.debugOptions, config.debugOptions)
        assertTrue(config.isTimeFrozen)

        val automatic = reducer.reduce(state, BlackHoleDebugEvent.OnManualCameraToggle(false)).toPreviewConfig()
        assertNull(automatic.manualElevationDeg)
        assertNull(automatic.manualAzimuthDeg)
    }

    @Test
    fun `stats and shader failures from the GL thread land in the state`() = runTest {
        var state = reducer.reduce(initial, BlackHoleDebugEvent.OnFrameStats(fps = 59.5, gpuMs = 7.25))
        state = reducer.reduce(state, BlackHoleDebugEvent.OnRenderFailure("lens.frag: syntax error"))

        assertEquals(59.5, state.fps)
        assertEquals(7.25, state.gpuMs)
        assertEquals("lens.frag: syntax error", state.renderFailure)
    }

    private fun tinyTables() = LensTables(LensTableKey(30.0, 1, 1, 7.0), FloatArray(2), FloatArray(2))
}
