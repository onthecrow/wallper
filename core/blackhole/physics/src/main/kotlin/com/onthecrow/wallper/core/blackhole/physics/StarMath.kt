package com.onthecrow.wallper.core.blackhole.physics

import kotlin.math.PI
import kotlin.math.sqrt

/** CPU-side constants of the star layers (§9.3). */
object StarMath {
    /** Mean of dΩ / (dq_u dq_v) over a cube face in the equi-angular mapping: (4π/6) / 4. */
    const val MEAN_CELL_JACOBIAN = PI / 6

    /** At most this many stars per cell: a star keeps inside [0.2, 0.8] of its cell, so a fuller grid looks regular. */
    const val MAX_CELL_DENSITY = 0.8

    /** The user's star sharpness, 0 (soft) to 1 (crisp), maps to the star size and the footprint widening. */
    const val DEFAULT_SHARPNESS = 0.35

    fun sizePx(sharpness: Double): Double = lerp(0.7, 0.25, sharpness.coerceIn(0.0, 1.0))

    fun footprintWidening(sharpness: Double): Double = lerp(0.25, 0.06, sharpness.coerceIn(0.0, 1.0))

    /** The sharpness that gives [sizePx]: the inverse of [sizePx]. */
    fun sharpnessOf(sizePx: Double): Double = ((sizePx - 0.7) / (0.25 - 0.7)).coerceIn(0.0, 1.0)

    /**
     * [layer] with [countScale] times its stars. While that fits [MAX_CELL_DENSITY] only the density
     * grows; beyond, the grid gets finer by k with the density lowered by k², so the count is exact.
     * The cost per pixel stays one hash per layer either way.
     */
    fun effectiveLayer(layer: StarLayer, countScale: Double): StarLayer {
        val density = layer.density * countScale.coerceAtLeast(0.0)
        if (density <= MAX_CELL_DENSITY) return layer.copy(density = density)
        val cells = Math.round(layer.cells * sqrt(density / MAX_CELL_DENSITY)).toInt()
        val finerDensity = density * layer.cells.toDouble() * layer.cells / (cells.toDouble() * cells)
        return StarLayer(cells = cells, density = finerDensity, flux = layer.flux)
    }

    /** σ in radians for [sizePx] render pixels at the centre of a [renderHeightPx] tall frame. */
    fun sigmaRad(sizePx: Double, tanHalfFovY: Double, renderHeightPx: Int): Double =
        sizePx * 2 * tanHalfFovY / renderHeightPx.coerceAtLeast(1)

    /**
     * Mean radiance of a layer whose stars got smaller than a pixel: density · E[mag] · 2πσ² per mean
     * cell solid angle. A fading layer is replaced by this, so compressed sky keeps its brightness.
     */
    fun averageRadiance(layer: StarLayer, sigmaRad: Double, magPower: Double): Double {
        val meanMagnitude = layer.flux / (magPower + 1)
        val meanCellSolidAngle = MEAN_CELL_JACOBIAN * 4.0 / (layer.cells.toDouble() * layer.cells)
        return layer.density * meanMagnitude * 2 * PI * sigmaRad * sigmaRad / meanCellSolidAngle
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
