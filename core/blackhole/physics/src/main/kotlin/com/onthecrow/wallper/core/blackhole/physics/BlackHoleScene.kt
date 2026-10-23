package com.onthecrow.wallper.core.blackhole.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.tanh

/** What the renderer needs for one frame besides [BlackHoleParams]; computed on the CPU. */
data class FrameState(
    val pose: CameraPose,
    val tanHalfFovY: Double,
    val principalX: Double,
    val principalY: Double,
    /** Disk texture phases A and B of the crossfade, and the mix between them (§8.3). */
    val diskPhaseA: Double,
    val diskPhaseB: Double,
    val diskFlowMix: Double,
    /** Turns of the inner edge per flow period. */
    val diskSpin: Double,
    val elevationDeg: Double,
    val azimuthDeg: Double,
    /** How far the recoil moves the hole, in half screen heights on both axes: the renderer scales x to the screen. */
    val recoilX: Double = 0.0,
    val recoilY: Double = 0.0,
)

/**
 * The CPU side of a frame (§7). The camera orbits about the disk axis; the gyroscope and the launcher
 * turn it on top in the picture's own axes, so a tilted disk doesn't tilt their motion. Every input goes
 * through a damped spring. While those turns move, the hole recoils the other way and springs back.
 * Also the disk phases, and whether something moves fast enough to need the active frame rate.
 */
class BlackHoleScene(params: BlackHoleParams = BlackHoleParams()) {

    var params: BlackHoleParams = params

    // Screen axes: pitch moves the camera up the picture, yaw to its right.
    private val gyroPitch = Spring()
    private val gyroYaw = Spring()
    private val launcher = Spring()
    // Disk axes, for the preview's drag: it turns the orbit itself.
    private val orbitElevation = Spring()
    private val orbitAzimuth = Spring()
    private val recoilX = Spring()
    private val recoilY = Spring()

    private var gyroPitchTarget = 0.0
    private var gyroYawTarget = 0.0
    private var launcherTarget = 0.0
    private var orbitElevationTarget = 0.0
    private var orbitAzimuthTarget = 0.0
    private var systemZoom = 0.0
    private var previousTargets: DoubleArray? = null
    private var lastNanos = Long.MIN_VALUE
    private var activeUntilNanos = Long.MIN_VALUE

    /** Debug overrides: when set they replace the automatic orbit. */
    var manualElevationDeg: Double? = null
    var manualAzimuthDeg: Double? = null

    /** In the picture's axes: [pitchDeg] turns the camera up the screen, [yawDeg] to the right. */
    fun setGyroTarget(pitchDeg: Double, yawDeg: Double) {
        gyroPitchTarget = if (params.input.gyroEnabled) pitchDeg else 0.0
        gyroYawTarget = if (params.input.gyroEnabled) yawDeg else 0.0
    }

    /** Turns the orbit itself, about the disk axis; the preview's drag. No recoil. */
    fun setOrbitOffset(elevationDeg: Double, azimuthDeg: Double) {
        orbitElevationTarget = elevationDeg
        orbitAzimuthTarget = azimuthDeg
    }

    /** `onOffsetsChanged`: [xOffset] 0..1 across the launcher pages; [hasPages] false when `xOffsetStep == 0`. */
    fun setLauncherOffset(xOffset: Float, hasPages: Boolean) {
        launcherTarget = if (params.input.launcherEnabled && hasPages) (xOffset - 0.5) * params.input.launcherAngleDeg else 0.0
    }

    /** `onZoomChanged`: 0 fully zoomed in, 1 fully zoomed out. The system animates it itself. */
    fun setSystemZoom(zoom: Float) {
        systemZoom = zoom.toDouble().coerceIn(0.0, 1.0)
    }

    /** Becoming visible: no jump from stale inputs. */
    fun resetInputs() {
        gyroPitchTarget = 0.0
        gyroYawTarget = 0.0
        gyroPitch.reset()
        gyroYaw.reset()
        launcher.reset(launcherTarget)
        recoilX.reset()
        recoilY.reset()
        previousTargets = null
        lastNanos = Long.MIN_VALUE
    }

    /** Inputs are moving or settling: draw at the active frame rate. */
    fun isActive(nanos: Long): Boolean = nanos < activeUntilNanos

    fun frame(nanos: Long): FrameState {
        val dt = if (lastNanos == Long.MIN_VALUE) 0.0 else ((nanos - lastNanos) / 1e9).coerceIn(0.0, 0.25)
        lastNanos = nanos
        val input = params.input
        val omega = input.springOmega
        val damping = input.springDamping
        gyroPitch.update(gyroPitchTarget, dt, omega, damping)
        gyroYaw.update(gyroYawTarget, dt, omega, damping)
        launcher.update(launcherTarget, dt, omega, damping)
        orbitElevation.update(orbitElevationTarget, dt, omega, damping)
        orbitAzimuth.update(orbitAzimuthTarget, dt, omega, damping)
        updateRecoil(dt)
        trackActivity(nanos, dt)

        val camera = params.camera
        val orbit = Phases.fraction(nanos, camera.orbitPeriodSec) * 360.0
        val swing = camera.elevationSwingDeg * sin(2 * PI * Phases.fraction(nanos, camera.elevationPeriodSec))
        val azimuth = (manualAzimuthDeg ?: (camera.startAzimuthDeg + orbit)) + orbitAzimuth.value
        val elevation = ((manualElevationDeg ?: (camera.elevationDeg + swing)) + orbitElevation.value)
            .coerceIn(camera.elevationMinDeg, camera.elevationMaxDeg)
        val pose = CameraPose.onOrbit(
            camera.distance, Math.toRadians(elevation), Math.toRadians(azimuth), Math.toRadians(camera.rollDeg),
        )
            .turned(yawRad = Math.toRadians(gyroYaw.value + launcher.value), pitchRad = Math.toRadians(gyroPitch.value))
            .withElevationWithin(Math.toRadians(camera.elevationMinDeg), Math.toRadians(camera.elevationMaxDeg))

        val disk = params.disk
        val spinning = disk.rotationPeriodSec > 0
        val flowPeriod = if (spinning) flowPeriodSec(disk) else 0.0
        val phaseA = Phases.fraction(nanos, flowPeriod)
        return FrameState(
            pose = pose,
            tanHalfFovY = tan(Math.toRadians(camera.fovYDeg) / 2) * (1 + camera.zoomOutFov * systemZoom),
            principalX = camera.principalX,
            principalY = camera.principalY,
            diskPhaseA = phaseA,
            diskPhaseB = (phaseA + 0.5).mod(1.0),
            // A stopped disk shows one layer: crossfading two still patterns would only pulse.
            diskFlowMix = if (spinning) abs(2 * phaseA - 1) else 0.0,
            diskSpin = if (spinning) flowPeriod / disk.rotationPeriodSec else 0.0,
            elevationDeg = Math.toDegrees(pose.elevationRad),
            azimuthDeg = Math.toDegrees(pose.azimuthRad).mod(360.0),
            recoilX = recoilX.value,
            recoilY = recoilY.value,
        )
    }

    /**
     * The hole leans against the turn: its target is the turn rate times the strength, pointing the other
     * way, so it builds up while the camera moves and springs back once it stops.
     */
    private fun updateRecoil(dt: Double) {
        val input = params.input
        val gain = RECOIL_PER_DEG_PER_SEC * input.recoilStrength
        val limit = input.recoilMaxShift
        val targetX = softClamp(-gain * (gyroYaw.velocity + launcher.velocity), limit)
        val targetY = softClamp(-gain * gyroPitch.velocity, limit)
        recoilX.update(targetX, dt, input.recoilReturnOmega)
        recoilY.update(targetY, dt, input.recoilReturnOmega)
    }

    private fun softClamp(value: Double, limit: Double) = if (limit > 0) limit * tanh(value / limit) else 0.0

    private companion object {
        const val ZOOM_ACTIVITY_SCALE = 10.0

        /** Half screen heights of recoil per °/s of turn at strength 1: 15 °/s, a quick page flick, gives 0.06. */
        const val RECOIL_PER_DEG_PER_SEC = 0.004

        /** Recoil this small is under a pixel: no need to keep drawing for it. */
        const val RECOIL_SETTLED = 0.001
    }

    /** The configured crossfade cycle, shortened so the pattern shifts by at most one turn per cycle (§8.3). */
    private fun flowPeriodSec(disk: DiskParams): Double {
        val differential = 1 - (disk.innerRadius / disk.outerRadius).pow(1.5)
        return min(disk.flowPeriodSec, disk.rotationPeriodSec / differential)
    }

    private fun trackActivity(nanos: Long, dt: Double) {
        // The zoom in degrees-per-second terms: a full zoom over a second is clearly motion.
        val targets = doubleArrayOf(
            gyroPitchTarget, gyroYawTarget, launcherTarget, orbitElevationTarget, orbitAzimuthTarget,
            systemZoom * ZOOM_ACTIVITY_SCALE,
        )
        val previous = previousTargets
        previousTargets = targets
        val input = params.input
        val fastTarget = previous != null && dt > 0 &&
            targets.indices.any { abs(targets[it] - previous[it]) / dt > input.activityDegPerSec }
        val settling = !gyroPitch.isSettled(gyroPitchTarget) ||
            !gyroYaw.isSettled(gyroYawTarget) ||
            !launcher.isSettled(launcherTarget) ||
            !orbitElevation.isSettled(orbitElevationTarget) ||
            !orbitAzimuth.isSettled(orbitAzimuthTarget) ||
            !recoilX.isSettled(0.0, RECOIL_SETTLED) ||
            !recoilY.isSettled(0.0, RECOIL_SETTLED)
        if (fastTarget || settling) activeUntilNanos = nanos + (input.activityHoldSec * 1e9).toLong()
    }
}
