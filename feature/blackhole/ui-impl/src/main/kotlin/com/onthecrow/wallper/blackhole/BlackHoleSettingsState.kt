package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.gl.PreviewConfig
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettings
import com.onthecrow.wallper.core.blackhole.renderer.lensTableKey
import com.onthecrow.wallper.core.mvi.State

internal data class BlackHoleSettingsState(
    /** What the screen shows: the stored settings, with a slider that is being dragged already applied. */
    val settings: BlackHoleSettings = BlackHoleSettings(),
    /** Debuggable builds only: the button that opens the debug renderer. */
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
) : State {

    val params: BlackHoleParams get() = settings.applyTo(BlackHoleParams())

    val quality: QualityLevel get() = settings.quality.startLevel

    val wantedTablesKey: LensTableKey get() = lensTableKey(params, quality)

    val isTablesLoading: Boolean get() = tablesKey != wantedTablesKey && tablesError == null
}

internal fun BlackHoleSettingsState.toPreviewConfig() = PreviewConfig(
    params = params,
    quality = quality,
    tables = tables,
    isTimeFrozen = isTimeFrozen,
)
