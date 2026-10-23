package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.core.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.core.blackhole.physics.lensTableKey
import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class BlackHoleDebugReducer @Inject constructor() : Reducer<BlackHoleDebugState, BlackHoleDebugEvent> {

    override suspend fun reduce(state: BlackHoleDebugState, event: BlackHoleDebugEvent): BlackHoleDebugState =
        reduceEvent(state, event).let { next ->
            // Every change of the params or the quality passes here.
            val key = lensTableKey(next.params, next.quality)
            if (key == next.wantedTablesKey) next else next.copy(wantedTablesKey = key)
        }

    private fun reduceEvent(state: BlackHoleDebugState, event: BlackHoleDebugEvent): BlackHoleDebugState =
        when (event) {
            is BlackHoleDebugEvent.OnSliderChange -> state.copy(params = event.slider.with(state.params, event.value))
            is BlackHoleDebugEvent.OnToggle -> state.copy(params = event.toggle.with(state.params, event.enabled))
            is BlackHoleDebugEvent.OnManualCameraToggle -> state.copy(isManualCamera = event.enabled)
            is BlackHoleDebugEvent.OnManualElevationChange -> state.copy(
                manualElevationDeg = event.degrees.coerceIn(BlackHoleSettings.ELEVATION_RANGE),
            )
            is BlackHoleDebugEvent.OnManualAzimuthChange -> state.copy(manualAzimuthDeg = event.degrees.coerceIn(0.0, 360.0))
            is BlackHoleDebugEvent.OnFreezeTimeToggle -> state.copy(isTimeFrozen = event.enabled)
            is BlackHoleDebugEvent.OnDistanceChange -> state.copy(
                draftDistance = event.distance.coerceIn(BlackHoleSettings.DISTANCE_RANGE),
            )
            BlackHoleDebugEvent.OnDistanceChangeFinished -> state.draftDistance?.let { distance ->
                state.copy(
                    params = state.params.copy(camera = state.params.camera.copy(distance = distance)),
                    draftDistance = null,
                    tablesError = null,
                )
            } ?: state
            is BlackHoleDebugEvent.OnQualityChange -> state.copy(quality = event.level, tablesError = null)
            is BlackHoleDebugEvent.OnLensModeChange -> state.copy(debugOptions = state.debugOptions.copy(lens = event.mode))
            is BlackHoleDebugEvent.OnDebugViewChange -> state.copy(debugOptions = state.debugOptions.copy(view = event.view))
            BlackHoleDebugEvent.OnPanelToggle -> state.copy(isPanelExpanded = !state.isPanelExpanded)
            // A late answer for a key the screen has moved on from is dropped.
            is BlackHoleDebugEvent.OnTablesLoaded -> if (event.key == state.wantedTablesKey) {
                state.copy(tables = event.tables, tablesKey = event.key, tablesError = null)
            } else {
                state
            }
            is BlackHoleDebugEvent.OnTablesLoadFailed -> if (event.key == state.wantedTablesKey) {
                state.copy(tablesError = event.message)
            } else {
                state
            }
            is BlackHoleDebugEvent.OnFrameStats -> state.copy(fps = event.fps, gpuMs = event.gpuMs)
            is BlackHoleDebugEvent.OnRenderFailure -> state.copy(renderFailure = event.message)
        }
}
