package com.onthecrow.wallper.blackhole.gl

import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import com.onthecrow.wallper.core.blackhole.renderer.DebugOptions

/** What a live preview draws; the screens build it from their state. Stats stay out, so they don't re-push it. */
internal data class PreviewConfig(
    val params: BlackHoleParams,
    val quality: QualityLevel,
    val tables: LensTables?,
    val debugOptions: DebugOptions = DebugOptions(),
    val manualElevationDeg: Double? = null,
    val manualAzimuthDeg: Double? = null,
    val isTimeFrozen: Boolean = false,
)

/** What a drag on the preview does. */
internal enum class DragMode {
    /** Emulates the gyroscope: a few degrees, back to the centre on release (debug screen). */
    GYRO,

    /** Turns the camera around the hole and leaves it there (settings screen). */
    ORBIT,
}
