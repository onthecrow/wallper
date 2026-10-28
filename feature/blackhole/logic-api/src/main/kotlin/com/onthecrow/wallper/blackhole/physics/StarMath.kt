package com.onthecrow.wallper.blackhole.physics

import kotlin.math.PI
import kotlin.math.sqrt

/** CPU-side constants of the star layers (§9.3). */
object StarMath {
    /** Mean of dΩ / (dq_u dq_v) over a cube face in the equi-angular mapping: (4π/6) / 4. */
    const val MEAN_CELL_JACOBIAN = PI / 6

    /** The smallest dΩ / (dq_u dq_v) on a cube face, at the middle of its edges: (π²/16) / √2. */
    val MIN_CELL_JACOBIAN = PI * PI / 16 / sqrt(2.0)

    /** At most this many stars per cell: a star keeps inside [0.2, 0.8] of its cell, so a fuller grid looks regular. */
    const val MAX_CELL_DENSITY = 0.8

    /** How far a star keeps from its cell's edge, as a share of the cell: [0.2, 0.8] above. */
    const val CELL_MARGIN = 0.2

    /** A star's σ fits this many times between it and its cell's edge, where it falls to 4% of its peak. */
    const val EDGE_SIGMAS = 2.5

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

    /**
     * σ in radians for [sizePx] screen pixels at the centre of a [screenHeightPx] tall screen. Screen, not
     * render pixels: a lower render scale must not make the stars bigger and brighter.
     */
    fun sigmaRad(sizePx: Double, tanHalfFovY: Double, screenHeightPx: Int): Double =
        sizePx * 2 * tanHalfFovY / screenHeightPx.coerceAtLeast(1)

    /**
     * σ in radians of the stars of [layer]: [sigmaRad], but no wider than its narrowest cells hold. The shader
     * reads no neighbouring cell, so a wider star would be cut off at the edge of its own into a square: the
     * layers of small cells, the faint stars, stop growing first.
     */
    fun layerSigmaRad(layer: StarLayer, sigmaRad: Double): Double {
        val narrowestCell = sqrt(MIN_CELL_JACOBIAN) * 2 / layer.cells.coerceAtLeast(1)
        return minOf(sigmaRad, CELL_MARGIN * narrowestCell / EDGE_SIGMAS)
    }

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
