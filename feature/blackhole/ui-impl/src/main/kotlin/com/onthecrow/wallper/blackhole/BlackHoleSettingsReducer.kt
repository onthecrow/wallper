package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.physics.lensTableKey
import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

/** User changes show up at once; the store echoes them back through [BlackHoleSettingsEvent.OnSettingsChanged]. */
internal class BlackHoleSettingsReducer @Inject constructor() :
    Reducer<BlackHoleSettingsState, BlackHoleSettingsEvent> {

    override suspend fun reduce(state: BlackHoleSettingsState, event: BlackHoleSettingsEvent): BlackHoleSettingsState =
        when (event) {
            is BlackHoleSettingsEvent.OnSettingsChanged -> state.withSettings(event.settings)
            is BlackHoleSettingsEvent.OnQualityChange -> state.edit { copy(quality = event.quality) }
            is BlackHoleSettingsEvent.OnGyroToggle -> state.edit { copy(gyroEnabled = event.enabled) }
            is BlackHoleSettingsEvent.OnLauncherToggle -> state.edit { copy(launcherEnabled = event.enabled) }
            is BlackHoleSettingsEvent.OnOrbitToggle -> state.edit { copy(orbitEnabled = event.enabled) }
            is BlackHoleSettingsEvent.OnZoomOutToggle -> state.edit { copy(zoomOutEnabled = event.enabled) }
            is BlackHoleSettingsEvent.OnDopplerToggle -> state.edit { copy(dopplerEnabled = event.enabled) }
            is BlackHoleSettingsEvent.OnOpacityFalloffToggle -> state.edit { copy(diskOpacityFalloff = event.enabled) }
            is BlackHoleSettingsEvent.OnOptimizedRenderingToggle -> state.edit { copy(optimizedRendering = event.enabled) }
            is BlackHoleSettingsEvent.OnLensCustomToggle -> state.edit { copy(lensCustom = event.enabled) }
            is BlackHoleSettingsEvent.OnLayerToggle -> state.edit { event.layer.with(this, event.enabled) }
            is BlackHoleSettingsEvent.OnIdleFpsChange -> state.edit { copy(idleFps = event.fps) }
            is BlackHoleSettingsEvent.OnSliderChange -> state.edit { event.slider.with(this, event.value) }
            is BlackHoleSettingsEvent.OnFreezeTimeToggle -> state.copy(isTimeFrozen = event.enabled)
            BlackHoleSettingsEvent.OnPanelToggle -> state.copy(isPanelExpanded = !state.isPanelExpanded)
            BlackHoleSettingsEvent.OnAdvancedToggle -> state.copy(isAdvancedExpanded = !state.isAdvancedExpanded)
            // A late answer for a key the screen has moved on from is dropped.
            is BlackHoleSettingsEvent.OnTablesLoaded -> if (event.key == state.wantedTablesKey) {
                state.copy(tables = event.tables, tablesKey = event.key, tablesError = null)
            } else {
                state
            }
            is BlackHoleSettingsEvent.OnTablesLoadFailed -> if (event.key == state.wantedTablesKey) {
                state.copy(tablesError = event.message)
            } else {
                state
            }
            is BlackHoleSettingsEvent.OnFrameStats -> state.copy(fps = event.fps, gpuMs = event.gpuMs)
            is BlackHoleSettingsEvent.OnRenderFailure -> state.copy(renderFailure = event.message)
            is BlackHoleSettingsEvent.OnSliderChangeFinished,
            BlackHoleSettingsEvent.OnShuffleStarsClick,
            BlackHoleSettingsEvent.OnResetClick,
            BlackHoleSettingsEvent.OnDebugClick -> state
        }

    private inline fun BlackHoleSettingsState.edit(transform: BlackHoleSettings.() -> BlackHoleSettings) =
        withSettings(settings.transform())

    /** Settings and what follows from them change together, and only when they really change. */
    private fun BlackHoleSettingsState.withSettings(next: BlackHoleSettings): BlackHoleSettingsState {
        if (next == settings) return this
        val params = next.applyTo(BlackHoleParams())
        return copy(settings = next, params = params, wantedTablesKey = lensTableKey(params, next.quality.startLevel))
    }
}
