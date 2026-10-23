package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.model.DebugSlider
import com.onthecrow.wallper.blackhole.model.DebugToggle
import com.onthecrow.wallper.blackhole.physics.LensTableKey
import com.onthecrow.wallper.blackhole.physics.LensTables
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.renderer.DebugView
import com.onthecrow.wallper.blackhole.renderer.LensMode
import com.onthecrow.wallper.core.mvi.Event

internal sealed interface BlackHoleDebugEvent : Event {
    // User actions
    data class OnSliderChange(val slider: DebugSlider, val value: Double) : BlackHoleDebugEvent
    data class OnToggle(val toggle: DebugToggle, val enabled: Boolean) : BlackHoleDebugEvent
    data class OnManualCameraToggle(val enabled: Boolean) : BlackHoleDebugEvent
    data class OnManualElevationChange(val degrees: Double) : BlackHoleDebugEvent
    data class OnManualAzimuthChange(val degrees: Double) : BlackHoleDebugEvent
    data class OnFreezeTimeToggle(val enabled: Boolean) : BlackHoleDebugEvent
    data class OnDistanceChange(val distance: Double) : BlackHoleDebugEvent
    /** New tables for the dragged distance. */
    data object OnDistanceChangeFinished : BlackHoleDebugEvent
    /** New tables for the level. */
    data class OnQualityChange(val level: QualityLevel) : BlackHoleDebugEvent
    data class OnLensModeChange(val mode: LensMode) : BlackHoleDebugEvent
    data class OnDebugViewChange(val view: DebugView) : BlackHoleDebugEvent
    data object OnPanelToggle : BlackHoleDebugEvent

    // Internal events
    data class OnTablesLoaded(val key: LensTableKey, val tables: LensTables) : BlackHoleDebugEvent
    data class OnTablesLoadFailed(val key: LensTableKey, val message: String) : BlackHoleDebugEvent

    // From the GL thread
    data class OnFrameStats(val fps: Double, val gpuMs: Double?) : BlackHoleDebugEvent
    data class OnRenderFailure(val message: String) : BlackHoleDebugEvent
}
