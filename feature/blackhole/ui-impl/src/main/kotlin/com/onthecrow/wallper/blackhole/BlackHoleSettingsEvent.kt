package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.model.PictureLayer
import com.onthecrow.wallper.blackhole.model.SettingsSlider
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualitySetting
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettings
import com.onthecrow.wallper.core.mvi.Event

internal sealed interface BlackHoleSettingsEvent : Event {
    // User actions, stored right away
    data class OnQualityChange(val quality: QualitySetting) : BlackHoleSettingsEvent
    data class OnGyroToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnLauncherToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnOrbitToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnZoomOutToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnDopplerToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnOpacityFalloffToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnLayerToggle(val layer: PictureLayer, val enabled: Boolean) : BlackHoleSettingsEvent
    data class OnIdleFpsChange(val fps: Int) : BlackHoleSettingsEvent
    data object OnShuffleStarsClick : BlackHoleSettingsEvent
    data object OnResetClick : BlackHoleSettingsEvent
    data object OnDebugClick : BlackHoleSettingsEvent

    // User actions, stored when the drag ends: a new distance regenerates the lens tables
    data class OnSliderChange(val slider: SettingsSlider, val value: Double) : BlackHoleSettingsEvent
    data class OnSliderChangeFinished(val slider: SettingsSlider) : BlackHoleSettingsEvent

    // The preview only, never stored
    data class OnFreezeTimeToggle(val enabled: Boolean) : BlackHoleSettingsEvent
    data object OnPanelToggle : BlackHoleSettingsEvent

    // Internal events
    data class OnSettingsChanged(val settings: BlackHoleSettings) : BlackHoleSettingsEvent
    data class OnTablesLoaded(val key: LensTableKey, val tables: LensTables) : BlackHoleSettingsEvent
    data class OnTablesLoadFailed(val key: LensTableKey, val message: String) : BlackHoleSettingsEvent

    // From the preview's GL thread
    data class OnFrameStats(val fps: Double, val gpuMs: Double?) : BlackHoleSettingsEvent
    data class OnRenderFailure(val message: String) : BlackHoleSettingsEvent
}
