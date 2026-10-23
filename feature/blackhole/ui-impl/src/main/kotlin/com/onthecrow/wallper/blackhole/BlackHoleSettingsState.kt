package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.gl.PreviewConfig
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import com.onthecrow.wallper.core.blackhole.physics.lensTableKey
import com.onthecrow.wallper.core.mvi.State

internal data class BlackHoleSettingsState(
    /** What the screen shows: the stored settings, with a slider that is being dragged already applied. */
    val settings: BlackHoleSettings = BlackHoleSettings(),
    /** [settings] on top of the defaults and the tables they need; the reducer keeps them in step. */
    val params: BlackHoleParams = settings.applyTo(BlackHoleParams()),
    val wantedTablesKey: LensTableKey = lensTableKey(params, settings.quality.startLevel),
    /** Debuggable builds only: the frame stats over the preview and the button to the debug renderer. */
    val isDebugAvailable: Boolean = false,
    /** Stops the preview's animation; never stored, the wallpaper doesn't see it. */
    val isTimeFrozen: Boolean = false,
    /** The newest tables that fit [wantedTablesKey]; older ones stay in the preview while new ones load. */
    val tables: LensTables? = null,
    val tablesKey: LensTableKey? = null,
    val tablesError: String? = null,
    /** Measured on the preview's GL thread, null until the first report. */
    val fps: Double? = null,
    val gpuMs: Double? = null,
    /** The shaders don't build on this device. */
    val renderFailure: String? = null,
    val isPanelExpanded: Boolean = true,
    val isAdvancedExpanded: Boolean = false,
) : State {

    val quality: QualityLevel get() = settings.quality.startLevel

    val isTablesLoading: Boolean get() = tablesKey != wantedTablesKey && tablesError == null
}

/** The tables the preview draws [this] with: those of the level its quality starts at. */
internal fun BlackHoleSettings.previewTablesKey(): LensTableKey =
    lensTableKey(applyTo(BlackHoleParams()), quality.startLevel)

internal fun BlackHoleSettingsState.toPreviewConfig() = PreviewConfig(
    params = params,
    quality = quality,
    tables = tables,
    isTimeFrozen = isTimeFrozen,
)
