package com.onthecrow.wallper.core.blackhole.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Photon orbits around a Schwarzschild hole with r_s = 1 (BLACKHOLE_TZ.md §5): `u'' = −u + 1.5u²`,
 * u = 1/r, ' = d/dφ, in the plane of the ray.
 */
object LensMath {
    /** Critical impact parameter 3√3/2. */
    const val B_C = 2.598076211353316

    /** Table rows cover φ up to this: enough for three disk crossings. */
    const val PHI_MAX = 3 * PI

    /** Rays winding further only add sub-pixel rings. */
    const val PHI_END_MAX = 8 * PI

    private const val H_MAX = 2e-3
    private const val C_STEP = 1e-3
    private const val ALPHA_EPS = 1e-7

    /** Angular radius of the shadow seen from [rc]. */
    fun alphaC(rc: Double): Double = asin(B_C * sqrt(1 - 1 / rc) / rc)

    /** Impact parameter of a ray leaving [rc] at the local angle [alpha] to the hole. */
    fun impactParameter(alpha: Double, rc: Double): Double = rc * sin(alpha) / sqrt(1 - 1 / rc)

    /** The ray of table column [t] ∈ [0, 1] on [branch] (0 captured, 1 escaped); dense near α_c (§5.2). */
    fun alphaOf(branch: Int, t: Double, rc: Double, k: Double): Double {
        val ac = alphaC(rc)
        val span = if (branch == 0) ac else PI - ac
        val d = span * (exp(k * t) - 1) / (exp(k) - 1)
        return (if (branch == 0) ac - d else ac + d).coerceIn(ALPHA_EPS, PI - ALPHA_EPS)
    }

    /** Inverse of [alphaOf], as the shader computes it. */
    fun tOf(alpha: Double, rc: Double, k: Double): Double {
        val ac = alphaC(rc)
        val span = if (alpha < ac) ac else PI - ac
        return ln(1 + abs(alpha - ac) * (exp(k) - 1) / span) / k
    }

    /**
     * Traces one ray and writes u at the φ grid nodes `j · phiMax / (out.size − 1)` into [out]; nodes past
     * the end hold 1 (captured) or 0 (escaped). Returns φ_end: where the ray crossed the horizon or
     * reached infinity, capped at [phiEndMax].
     */
    fun traceRay(alpha: Double, rc: Double, out: FloatArray, phiMax: Double = PHI_MAX, phiEndMax: Double = PHI_END_MAX): Double {
        val nPhi = out.size
        val grid = phiMax / (nPhi - 1)
        val photon = Photon(alpha, rc)
        out[0] = photon.u.toFloat()
        var j = 1
        while (photon.phi < phiEndMax) {
            var h = photon.naturalStep()
            val node = j * grid
            val hitsNode = j < nPhi && photon.phi + h >= node
            if (hitsNode) h = node - photon.phi
            val u0 = photon.u
            val phi0 = photon.phi
            photon.advance(h)
            if (photon.u >= 1.0) {
                out.fill(1f, j, nPhi)
                return phi0 + h * (1.0 - u0) / (photon.u - u0)
            }
            if (photon.u <= 0.0) {
                out.fill(0f, j, nPhi)
                return phi0 + h * u0 / (u0 - photon.u)
            }
            if (hitsNode) out[j++] = photon.u.toFloat()
        }
        if (j < nPhi) out.fill(photon.u.toFloat(), j, nPhi)
        return phiEndMax
    }

    /** One photon integrated with RK4 over φ. Allocation-free: tables trace millions of steps. */
    class Photon(alpha: Double, rc: Double) {
        var u = 1 / rc
            private set
        /** u'. Positive while the ray approaches the hole. */
        var v = u * sqrt(1 - u) / tan(alpha)
            private set
        var phi = 0.0
            private set

        /** Small near the photon sphere, where errors grow as e^φ. */
        fun naturalStep(): Double = min(H_MAX, C_STEP / max(abs(v), 1e-12))

        fun advance(h: Double) {
            val k1u = v
            val k1v = accel(u)
            val k2u = v + 0.5 * h * k1v
            val k2v = accel(u + 0.5 * h * k1u)
            val k3u = v + 0.5 * h * k2v
            val k3v = accel(u + 0.5 * h * k2u)
            val k4u = v + h * k3v
            val k4v = accel(u + h * k3u)
            u += h / 6 * (k1u + 2 * k2u + 2 * k3u + k4u)
            v += h / 6 * (k1v + 2 * k2v + 2 * k3v + k4v)
            phi += h
        }

        /** (u')² + u² − u³, which equals 1/b² along the exact orbit. */
        fun firstIntegral(): Double = v * v + u * u - u * u * u

        private fun accel(u: Double) = -u + 1.5 * u * u
    }
}
