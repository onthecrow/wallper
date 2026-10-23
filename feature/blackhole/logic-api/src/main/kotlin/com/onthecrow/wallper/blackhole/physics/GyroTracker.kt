package com.onthecrow.wallper.blackhole.physics

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Turns device orientation samples into small camera turns in the screen's axes (§7.2): tilting the
 * phone forward pitches the camera up the picture, turning it sideways yaws it, whatever the disk does.
 * The offset is measured against a reference that slowly follows the device, so a new grip drifts back
 * to the centre, is soft-clamped and then goes through a 1€ filter against hand tremor.
 */
class GyroTracker {
    private var reference: Quaternion? = null

    var targetPitchDeg = 0.0
        private set
    var targetYawDeg = 0.0
        private set

    private val pitchFilter = OneEuroFilter()
    private val yawFilter = OneEuroFilter()

    /** Becoming visible: the current orientation is the new centre. */
    fun reset() {
        reference = null
        targetPitchDeg = 0.0
        targetYawDeg = 0.0
        pitchFilter.reset()
        yawFilter.reset()
    }

    /**
     * @param orientation the device rotation, e.g. from `TYPE_GAME_ROTATION_VECTOR`.
     * @param displayRotationDeg 0, 90, 180 or 270: how the screen is turned against the device's natural orientation.
     */
    fun onSample(orientation: Quaternion, dtSec: Double, displayRotationDeg: Int, params: InputParams) {
        val current = orientation.normalized()
        val ref = reference?.let { Quaternion.slerp(it, current, 1 - exp(-dtSec / params.gyroRecenterSec)) } ?: current
        reference = ref
        var relative = ref.conjugate() * current
        if (relative.w < 0) relative = Quaternion(-relative.x, -relative.y, -relative.z, -relative.w)
        // Small angles: (rx, ry, rz) ≈ 2 · xyz, in radians about the device axes.
        val rx = 2 * relative.x
        val ry = 2 * relative.y
        // Device axes to screen axes, as SensorManager.remapCoordinateSystem does for each display rotation.
        val (screenX, screenY) = when (displayRotationDeg) {
            90 -> ry to -rx
            180 -> -rx to -ry
            270 -> -ry to rx
            else -> rx to ry
        }
        val pitch = Math.toDegrees(screenX) * params.gyroGain * if (params.gyroInvertPitch) -1 else 1
        val yaw = Math.toDegrees(screenY) * params.gyroGain * if (params.gyroInvertYaw) -1 else 1
        targetPitchDeg = smooth(pitchFilter, softClamp(pitch, params.gyroMaxAngleDeg), dtSec, params.gyroSmoothing)
        targetYawDeg = smooth(yawFilter, softClamp(yaw, params.gyroMaxAngleDeg), dtSec, params.gyroSmoothing)
    }

    private fun softClamp(value: Double, limit: Double) = limit * tanh(value / limit)

    private fun smooth(filter: OneEuroFilter, value: Double, dtSec: Double, smoothing: Double): Double {
        if (smoothing <= 0) return value
        // Smoothing 0..1 maps to a resting cutoff of 10 Hz (barely any) down to 0.3 Hz (heavy).
        val minCutoffHz = 10.0.pow(1 - 1.5 * smoothing.coerceAtMost(1.0))
        return filter.filter(value, dtSec, minCutoffHz, SPEED_COEFFICIENT)
    }

    private companion object {
        /** How fast the cutoff opens with the speed of the offset, in Hz per °/s. */
        const val SPEED_COEFFICIENT = 0.15
    }
}
