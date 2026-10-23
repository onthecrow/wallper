package com.onthecrow.wallper.core.blackhole.physics

import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BLACKHOLE_TZ.md §5.5. */
class LensMathTest {

    private val rc = 32.0

    @Test
    fun `the capture boundary found by the integrator is alpha_c`() {
        var captured = 0.0 // α below this falls in
        var escaped = PI / 2
        repeat(40) {
            val mid = (captured + escaped) / 2
            if (isCaptured(mid)) captured = mid else escaped = mid
        }

        assertEquals(LensMath.alphaC(rc), (captured + escaped) / 2, 1e-6)
    }

    @Test
    fun `the first integral holds along trajectories`() {
        for (alpha in listOf(0.03, 0.06, 0.1, 0.5, 1.2, 2.5)) {
            val photon = LensMath.Photon(alpha, rc)
            val expected = 1 / LensMath.impactParameter(alpha, rc).let { it * it }
            while (photon.phi < 2 * PI && photon.u in 0.0..0.99) {
                photon.advance(photon.naturalStep())
                assertTrue(abs(photon.firstIntegral() - expected) < 1e-9, "α=$alpha at φ=${photon.phi}")
            }
        }
    }

    @Test
    fun `weak field deflection is 2 over b`() {
        val farRc = 1e6
        for (b in listOf(500.0, 1000.0, 2000.0, 5000.0)) {
            val alpha = asin(b * sqrt(1 - 1 / farRc) / farRc)
            val phiInf = LensMath.traceRay(alpha, farRc, FloatArray(2))

            val deflection = phiInf - (PI - alpha)
            assertEquals(2 / b, deflection, 0.01 * 2 / b, "b=$b")
        }
    }

    @Test
    fun `phi_end grows towards the shadow edge on both branches`() = runBlocking {
        val key = LensTableKey(rc, nAlpha = 128, nPhi = 64, k = 7.0)
        val tables = LensTables.generate(key)

        for (branch in 0..1) {
            for (i in 1 until key.nAlpha) {
                val nearer = tables.phiEnd(branch, i - 1)
                val farther = tables.phiEnd(branch, i)
                assertTrue(nearer >= farther - 1e-4f, "branch $branch, t index $i: $nearer < $farther")
            }
        }
    }

    @Test
    fun `table columns map back to their t`() {
        for (branch in 0..1) {
            for (t in listOf(0.0, 0.1, 0.5, 0.9)) {
                val alpha = LensMath.alphaOf(branch, t, rc, 7.0)
                assertEquals(t, LensMath.tOf(alpha, rc, 7.0), 1e-6)
            }
        }
    }

    private fun isCaptured(alpha: Double): Boolean {
        val photon = LensMath.Photon(alpha, rc)
        while (photon.phi < LensMath.PHI_END_MAX * 2) {
            photon.advance(photon.naturalStep() / 4)
            if (photon.u >= 1) return true
            if (photon.u <= 0) return false
        }
        error("α=$alpha neither fell in nor escaped")
    }
}
