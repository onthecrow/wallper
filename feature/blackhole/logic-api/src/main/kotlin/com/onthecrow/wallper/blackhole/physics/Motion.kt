package com.onthecrow.wallper.blackhole.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Animation phases in [0, 1), computed from Long nanoseconds so they stay exact after days of uptime (§1.1). */
object Phases {
    fun fraction(nanos: Long, periodSec: Double): Double {
        if (periodSec <= 0) return 0.0
        val period = (periodSec * 1e9).toLong().coerceAtLeast(1)
        return Math.floorMod(nanos, period).toDouble() / period
    }
}

/**
 * A damped spring, integrated exactly, so any frame time is stable. `damping` is ζ: 1 is critical, the
 * fastest approach without overshoot; below 1 it swings past the target and back, above 1 it creeps in.
 */
class Spring(initial: Double = 0.0) {
    var value = initial
        private set
    var velocity = 0.0
        private set

    fun update(target: Double, dtSec: Double, omega: Double, damping: Double = 1.0): Double {
        val x0 = value - target
        val v0 = velocity
        val zeta = damping.coerceAtLeast(MIN_DAMPING)
        when {
            abs(zeta - 1) < CRITICAL_TOLERANCE -> {
                val c2 = v0 + omega * x0
                val decay = exp(-omega * dtSec)
                value = target + (x0 + c2 * dtSec) * decay
                velocity = (v0 - omega * c2 * dtSec) * decay
            }
            zeta < 1 -> {
                val omegaD = omega * sqrt(1 - zeta * zeta)
                val decay = exp(-zeta * omega * dtSec)
                val cos = cos(omegaD * dtSec)
                val sin = sin(omegaD * dtSec)
                value = target + decay * (x0 * cos + (v0 + zeta * omega * x0) / omegaD * sin)
                velocity = decay * (v0 * cos - (omega * omega * x0 + zeta * omega * v0) / omegaD * sin)
            }
            else -> {
                val spread = omega * sqrt(zeta * zeta - 1)
                val slow = -zeta * omega + spread
                val fast = -zeta * omega - spread
                val cFast = (v0 - slow * x0) / (fast - slow)
                val cSlow = x0 - cFast
                val eSlow = exp(slow * dtSec)
                val eFast = exp(fast * dtSec)
                value = target + cSlow * eSlow + cFast * eFast
                velocity = slow * cSlow * eSlow + fast * cFast * eFast
            }
        }
        return value
    }

    fun reset(value: Double = 0.0) {
        this.value = value
        velocity = 0.0
    }

    /** Close enough to [target] and slow enough that drawing faster shows nothing. */
    fun isSettled(target: Double, epsilon: Double = 0.01): Boolean =
        abs(value - target) < epsilon && abs(velocity) < epsilon

    private companion object {
        const val MIN_DAMPING = 0.05
        const val CRITICAL_TOLERANCE = 1e-6
    }
}

/**
 * The 1€ filter (Casiez, Roussel, Vogel; CHI 2012): smooths hard while the signal moves slowly, which
 * takes out hand tremor, and less as it speeds up, so a deliberate move doesn't lag.
 */
class OneEuroFilter {
    private var value = 0.0
    private var derivative = 0.0
    private var started = false

    fun filter(x: Double, dtSec: Double, minCutoffHz: Double, beta: Double, derivativeCutoffHz: Double = 1.0): Double {
        if (!started) {
            value = x
            derivative = 0.0
            started = true
            return value
        }
        if (dtSec <= 0) return value
        derivative += alpha(derivativeCutoffHz, dtSec) * ((x - value) / dtSec - derivative)
        value += alpha(minCutoffHz + beta * abs(derivative), dtSec) * (x - value)
        return value
    }

    fun reset() {
        started = false
    }

    private fun alpha(cutoffHz: Double, dtSec: Double): Double = 1 / (1 + 1 / (2 * PI * cutoffHz * dtSec))
}
