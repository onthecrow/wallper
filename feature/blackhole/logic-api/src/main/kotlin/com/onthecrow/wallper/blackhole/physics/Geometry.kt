package com.onthecrow.wallper.blackhole.physics

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(this dot this)
    fun normalized() = this * (1 / length)

    /** Turned by [angleRad] about the unit [axis], counter-clockwise looking down it (Rodrigues). */
    fun rotated(axis: Vec3, angleRad: Double): Vec3 {
        val cos = cos(angleRad)
        return this * cos + (axis cross this) * sin(angleRad) + axis * ((axis dot this) * (1 - cos))
    }

    companion object {
        val UP = Vec3(0.0, 1.0, 0.0)
    }
}

/** A camera on the sphere of radius |position| around the hole, looking at its centre (§7.1). */
data class CameraPose(val position: Vec3, val right: Vec3, val up: Vec3, val forward: Vec3) {

    /** θ: height above the disk plane. */
    val elevationRad: Double get() = asin((position.y / position.length).coerceIn(-1.0, 1.0))

    /** ψ, as in [onOrbit]. */
    val azimuthRad: Double get() = atan2(position.x, position.z)

    /**
     * The whole camera turned around the hole in the picture's own axes: by [yawRad] the camera moves to
     * the right of the picture, by [pitchRad] up it. The disk tilt on screen stays as it was.
     */
    fun turned(yawRad: Double, pitchRad: Double): CameraPose {
        if (yawRad == 0.0 && pitchRad == 0.0) return this
        val yawed = rotated(up, yawRad)
        return yawed.rotated(-yawed.right, pitchRad)
    }

    /** Kept between [minRad] and [maxRad] above the disk by turning the whole camera as little as needed. */
    fun withElevationWithin(minRad: Double, maxRad: Double): CameraPose {
        val elevation = elevationRad
        val clamped = elevation.coerceIn(minRad, maxRad)
        val axis = position cross Vec3.UP // turning about it raises the camera
        if (clamped == elevation || axis.length < 1e-9) return this
        return rotated(axis.normalized(), clamped - elevation)
    }

    private fun rotated(axis: Vec3, angleRad: Double) = CameraPose(
        position.rotated(axis, angleRad),
        right.rotated(axis, angleRad),
        up.rotated(axis, angleRad),
        forward.rotated(axis, angleRad),
    )

    companion object {
        /** C = r · (cos θ · sin ψ, sin θ, cos θ · cos ψ); [rollRad] turns the picture counter-clockwise. */
        fun onOrbit(distance: Double, elevationRad: Double, azimuthRad: Double, rollRad: Double = 0.0): CameraPose {
            val position = Vec3(
                cos(elevationRad) * sin(azimuthRad),
                sin(elevationRad),
                cos(elevationRad) * cos(azimuthRad),
            ) * distance
            val forward = (-position).normalized()
            val level = (forward cross Vec3.UP).normalized()
            val levelUp = level cross forward
            // Rolling the camera clockwise turns the picture counter-clockwise.
            val right = level * cos(rollRad) - levelUp * sin(rollRad)
            val up = levelUp * cos(rollRad) + level * sin(rollRad)
            return CameraPose(position, right, up, forward)
        }
    }
}

/** A unit quaternion; [w] is the scalar part. */
data class Quaternion(val x: Double, val y: Double, val z: Double, val w: Double) {
    operator fun times(o: Quaternion) = Quaternion(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )

    /** The inverse of a unit quaternion. */
    fun conjugate() = Quaternion(-x, -y, -z, w)

    infix fun dot(o: Quaternion) = x * o.x + y * o.y + z * o.z + w * o.w

    fun normalized(): Quaternion {
        val n = sqrt(this dot this)
        return Quaternion(x / n, y / n, z / n, w / n)
    }

    companion object {
        val IDENTITY = Quaternion(0.0, 0.0, 0.0, 1.0)

        fun slerp(a: Quaternion, b: Quaternion, t: Double): Quaternion {
            var target = b
            var cosAngle = a dot b
            if (cosAngle < 0) { // the shorter way round
                target = Quaternion(-b.x, -b.y, -b.z, -b.w)
                cosAngle = -cosAngle
            }
            if (cosAngle > 0.9995) {
                return Quaternion(
                    a.x + t * (target.x - a.x),
                    a.y + t * (target.y - a.y),
                    a.z + t * (target.z - a.z),
                    a.w + t * (target.w - a.w),
                ).normalized()
            }
            val angle = acos(cosAngle)
            val sinAngle = sin(angle)
            val wa = sin((1 - t) * angle) / sinAngle
            val wb = sin(t * angle) / sinAngle
            return Quaternion(
                wa * a.x + wb * target.x,
                wa * a.y + wb * target.y,
                wa * a.z + wb * target.z,
                wa * a.w + wb * target.w,
            )
        }
    }
}
