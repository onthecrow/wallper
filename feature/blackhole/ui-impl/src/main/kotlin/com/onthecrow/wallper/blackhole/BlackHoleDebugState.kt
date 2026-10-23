package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.gl.PreviewConfig
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettings
import com.onthecrow.wallper.core.blackhole.renderer.DebugOptions
import com.onthecrow.wallper.core.blackhole.renderer.lensTableKey
import com.onthecrow.wallper.core.mvi.State

/** Everything here is local to the debug screen: neither the wallpaper nor the stored settings see it. */
internal data class BlackHoleDebugState(
    val params: BlackHoleParams,
    /** Fixed by hand here, no automatic choice; starts from the stored one, Auto on Medium. */
    val quality: QualityLevel,
    val debugOptions: DebugOptions = DebugOptions(),
    /** θ and ψ below replace the orbit. */
    val isManualCamera: Boolean = false,
    val manualElevationDeg: Double,
    val manualAzimuthDeg: Double = 0.0,
    val isTimeFrozen: Boolean = false,
    /** The distance slider while it is dragged; it reaches [params] when the drag ends, and new tables follow. */
    val draftDistance: Double? = null,
    /** The newest tables that fit [wantedTablesKey]; older ones stay on screen while new ones load. */
    val tables: LensTables? = null,
    val tablesKey: LensTableKey? = null,
    val tablesError: String? = null,
    /** Measured on the GL thread, null until the first report. */
    val fps: Double? = null,
    val gpuMs: Double? = null,
    /** The shaders don't build on this device. */
    val renderFailure: String? = null,
    val isPanelExpanded: Boolean = true,
) : State {

    val wantedTablesKey: LensTableKey get() = lensTableKey(params, quality)

    val isTablesLoading: Boolean get() = tablesKey != wantedTablesKey && tablesError == null

    /** What the distance slider shows. */
    val distance: Double get() = draftDistance ?: params.camera.distance

    companion object {
        /** The stored settings on top of the defaults, as the wallpaper starts. */
        fun initial(settings: BlackHoleSettings): BlackHoleDebugState {
            val params = settings.applyTo(BlackHoleParams())
            return BlackHoleDebugState(
                params = params,
                quality = settings.quality.startLevel,
                manualElevationDeg = params.camera.elevationDeg,
            )
        }
    }
}

internal fun BlackHoleDebugState.toPreviewConfig() = PreviewConfig(
    params = params,
    quality = quality,
    tables = tables,
    debugOptions = debugOptions,
    manualElevationDeg = manualElevationDeg.takeIf { isManualCamera },
    manualAzimuthDeg = manualAzimuthDeg.takeIf { isManualCamera },
    isTimeFrozen = isTimeFrozen,
)
