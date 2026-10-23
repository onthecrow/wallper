package com.onthecrow.wallper.core.blackhole.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionTest {

    @Test
    fun `phases stay exact after a long uptime`() {
        val tenDays = 10L * 24 * 3600 * 1_000_000_000
        val quarter = 10_000_000_000L // 10 s of a 40 s period

        assertEquals(0.25, Phases.fraction(tenDays + quarter, 40.0), 1e-12)
        assertEquals(0.0, Phases.fraction(123, 0.0))
    }

    @Test
    fun `the spring settles on its target without overshoot`() {
        val spring = Spring()
        var maxValue = 0.0
        repeat(120) { maxValue = maxOf(maxValue, spring.update(target = 5.0, dtSec = 1 / 60.0, omega = 10.0)) }

        assertTrue(maxValue <= 5.0 + 1e-9)
        assertTrue(spring.isSettled(5.0))
    }

    @Test
    fun `the spring is stable with long frames at any damping`() {
        listOf(0.3, 1.0, 2.0).forEach { damping ->
            val spring = Spring()
            repeat(40) { spring.update(target = 1.0, dtSec = 0.25, omega = 10.0, damping = damping) }

            assertEquals(1.0, spring.value, 1e-3, "damping $damping")
        }
    }

    @Test
    fun `a loose spring swings past and back, a stiff one creeps in`() {
        val loose = Spring()
        val stiff = Spring()
        val critical = Spring()
        var looseMax = 0.0
        var stiffMax = 0.0
        repeat(120) {
            looseMax = maxOf(looseMax, loose.update(1.0, 1 / 60.0, omega = 10.0, damping = 0.3))
            stiffMax = maxOf(stiffMax, stiff.update(1.0, 1 / 60.0, omega = 10.0, damping = 2.0))
            critical.update(1.0, 1 / 60.0, omega = 10.0)
        }
        val stiffAfterHalfSecond = Spring().apply { repeat(30) { update(1.0, 1 / 60.0, omega = 10.0, damping = 2.0) } }.value
        val criticalAfterHalfSecond = Spring().apply { repeat(30) { update(1.0, 1 / 60.0, omega = 10.0) } }.value

        assertTrue(looseMax > 1.3)
        assertTrue(stiffMax <= 1.0 + 1e-9)
        assertTrue(stiffAfterHalfSecond < criticalAfterHalfSecond)
        assertTrue(loose.isSettled(1.0, 0.02) && critical.isSettled(1.0))
    }

    @Test
    fun `the 1 euro filter calms tremor and lets a move through`() {
        val filter = OneEuroFilter()
        val dt = 0.02
        var calmed = 0.0
        repeat(200) { i ->
            val tremor = 0.3 * sin(2 * PI * 8 * i * dt) // an 8 Hz shake
            val out = filter.filter(tremor, dt, minCutoffHz = 1.0, beta = 0.15)
            if (i > 100) calmed = maxOf(calmed, abs(out))
        }
        repeat(100) { filter.filter(5.0, dt, minCutoffHz = 1.0, beta = 0.15) }

        assertTrue(calmed < 0.1)
        assertEquals(5.0, filter.filter(5.0, dt, minCutoffHz = 1.0, beta = 0.15), 0.05)
    }

    @Test
    fun `the camera looks at the hole from its orbit`() {
        val pose = CameraPose.onOrbit(distance = 32.0, elevationRad = Math.toRadians(6.0), azimuthRad = 1.0)

        assertEquals(32.0, pose.position.length, 1e-9)
        assertEquals(-1.0, pose.forward dot pose.position.normalized(), 1e-9)
        assertEquals(0.0, pose.right dot pose.up, 1e-9)
        assertTrue(pose.up.y > 0)
    }

    @Test
    fun `the gyro recentres on a new grip`() {
        val tracker = GyroTracker()
        val params = InputParams(gyroSmoothing = 0.0)
        val tilted = rotationAboutX(Math.toRadians(4.0))
        tracker.onSample(Quaternion.IDENTITY, 0.02, 0, params)

        tracker.onSample(tilted, 0.02, 0, params)
        val right = tracker.targetPitchDeg
        repeat(1000) { tracker.onSample(tilted, 0.02, 0, params) }

        assertEquals(2.0, right, 0.1) // 4° tilt · gain 0.5
        assertTrue(abs(tracker.targetPitchDeg) < 0.05)
    }

    @Test
    fun `big tilts are soft-clamped`() {
        val tracker = GyroTracker()
        val params = InputParams(gyroSmoothing = 0.0)
        tracker.onSample(Quaternion.IDENTITY, 0.02, 0, params)

        tracker.onSample(rotationAboutX(Math.toRadians(60.0)), 0.02, 0, params)

        assertTrue(tracker.targetPitchDeg < params.gyroMaxAngleDeg)
        assertTrue(tracker.targetPitchDeg > params.gyroMaxAngleDeg * 0.9)
    }

    @Test
    fun `gyro smoothing takes the shake out of a steady grip`() {
        fun shakiness(smoothing: Double): Double {
            val tracker = GyroTracker()
            val params = InputParams(gyroSmoothing = smoothing, gyroRecenterSec = 1e9)
            var spread = 0.0
            repeat(300) { i ->
                val shake = Math.toRadians(0.4 * sin(2 * PI * 9 * i * 0.02))
                tracker.onSample(rotationAboutX(shake), 0.02, 0, params)
                if (i > 150) spread = maxOf(spread, abs(tracker.targetPitchDeg))
            }
            return spread
        }

        assertTrue(shakiness(1.0) < shakiness(0.0) / 3)
    }

    @Test
    fun `moving inputs keep the scene active for the hold time`() {
        val scene = BlackHoleScene()
        val frame = 16_666_667L
        scene.frame(0)
        assertFalse(scene.isActive(0))

        scene.setGyroTarget(pitchDeg = 3.0, yawDeg = 0.0)
        scene.frame(frame)

        assertTrue(scene.isActive(frame))
        var t = frame
        repeat(600) { t += frame; scene.frame(t) }
        assertFalse(scene.isActive(t))
    }

    @Test
    fun `elevation stays within its limits`() {
        val scene = BlackHoleScene(BlackHoleParams(camera = CameraParams(elevationDeg = 1.0, elevationSwingDeg = 3.0)))

        val lowest = (0 until 120).minOf { scene.frame(it * 1_000_000_000L).elevationDeg }

        assertEquals(1.0, lowest, 1e-9)
    }

    @Test
    fun `the disk layers swap while invisible`() {
        // Slow enough that the scene keeps the 40 s flow period.
        val scene = BlackHoleScene(BlackHoleParams(disk = DiskParams(rotationPeriodSec = 40.0)))

        val start = scene.frame(0)
        val half = scene.frame(20_000_000_000L) // half of the 40 s flow period

        assertEquals(1.0, start.diskFlowMix, 1e-9) // layer A at its wrap, fully B
        assertEquals(0.0, half.diskFlowMix, 1e-9) // layer B at its wrap, fully A
        assertEquals(0.0, half.diskPhaseB, 1e-9)
    }

    @Test
    fun `roll turns the picture about the view axis`() {
        val level = CameraPose.onOrbit(32.0, Math.toRadians(6.0), 0.5)
        val rolled = CameraPose.onOrbit(32.0, Math.toRadians(6.0), 0.5, rollRad = Math.toRadians(30.0))

        assertEquals(level.forward, rolled.forward)
        assertEquals(0.0, rolled.right dot rolled.up, 1e-9)
        assertEquals(Math.cos(Math.toRadians(30.0)), rolled.up dot level.up, 1e-9)
        // The camera rolls clockwise, so its up leans to the old right: the picture turns counter-clockwise.
        assertTrue(rolled.up dot level.right > 0)
    }

    @Test
    fun `a fast disk shortens the crossfade so the pattern never doubles`() {
        val disk = DiskParams(rotationPeriodSec = 10.0, flowPeriodSec = 40.0)
        val scene = BlackHoleScene(BlackHoleParams(disk = disk))

        val spin = scene.frame(0).diskSpin
        val shift = spin * (1 - Math.pow(disk.innerRadius / disk.outerRadius, 1.5))

        assertEquals(1.0, shift, 1e-9)
    }

    @Test
    fun `the system zoom widens the view and counts as motion`() {
        val scene = BlackHoleScene()
        val base = scene.frame(0).tanHalfFovY

        scene.setSystemZoom(1f)
        val zoomed = scene.frame(16_666_667L)

        assertEquals(base * (1 + CameraParams().zoomOutFov), zoomed.tanHalfFovY, 1e-12)
        assertTrue(scene.isActive(16_666_667L))
    }

    @Test
    fun `zoom out can be turned off`() {
        val scene = BlackHoleScene(BlackHoleParams(camera = CameraParams(zoomOutFov = 0.0)))
        val base = scene.frame(0).tanHalfFovY

        scene.setSystemZoom(1f)

        assertEquals(base, scene.frame(1).tanHalfFovY, 1e-12)
    }

    @Test
    fun `a stopped disk shows one still layer`() {
        val scene = BlackHoleScene(BlackHoleParams(disk = DiskParams(rotationPeriodSec = 0.0)))

        val frame = scene.frame(12_345_000_000L)

        assertEquals(0.0, frame.diskSpin)
        assertEquals(0.0, frame.diskFlowMix)
    }

    private fun rotationAboutX(angle: Double) =
        Quaternion(kotlin.math.sin(angle / 2), 0.0, 0.0, kotlin.math.cos(angle / 2))
}
