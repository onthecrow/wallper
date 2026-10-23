package com.onthecrow.wallper.core.blackhole.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneInputTest {

    private val frame = 16_666_667L

    /** A still orbit with the disk tilted 45° on screen, so disk axes and screen axes differ. */
    private fun scene(input: InputParams = InputParams(), elevationDeg: Double = 10.0) = BlackHoleScene(
        BlackHoleParams(
            camera = CameraParams(elevationDeg = elevationDeg, elevationSwingDeg = 0.0, orbitPeriodSec = 0.0, rollDeg = 45.0),
            input = input,
        ),
    )

    private fun BlackHoleScene.run(seconds: Double, from: Long = 0L): Pair<FrameState, Long> {
        var t = from
        var state = frame(t)
        repeat((seconds * 60).toInt()) {
            t += frame
            state = frame(t)
        }
        return state to t
    }

    @Test
    fun `the gyroscope turns the camera along the screen, not the tilted disk`() {
        val pitched = scene()
        val base = pitched.frame(0).pose
        pitched.setGyroTarget(pitchDeg = 3.0, yawDeg = 0.0)
        val up = pitched.run(3.0).first.pose.position - base.position

        val yawed = scene()
        yawed.frame(0)
        yawed.setGyroTarget(pitchDeg = 0.0, yawDeg = 3.0)
        val right = yawed.run(3.0).first.pose.position - base.position

        assertTrue(up dot base.up > 0)
        assertEquals(0.0, (up dot base.right) / up.length, 0.03)
        assertTrue(right dot base.right > 0)
        assertEquals(0.0, (right dot base.up) / right.length, 0.03)
    }

    @Test
    fun `the launcher turns the camera along the screen too`() {
        val scene = scene()
        val base = scene.frame(0).pose
        scene.setLauncherOffset(1f, hasPages = true)
        val moved = scene.run(3.0).first.pose.position - base.position

        assertTrue(moved dot base.right > 0)
        assertEquals(0.0, (moved dot base.up) / moved.length, 0.03)
    }

    @Test
    fun `a turn below the disk stops at the lowest elevation`() {
        val scene = scene(elevationDeg = 2.0)
        scene.frame(0)
        scene.setGyroTarget(pitchDeg = -20.0, yawDeg = 0.0)

        val state = scene.run(3.0).first

        assertEquals(CameraParams().elevationMinDeg, state.elevationDeg, 1e-6)
        assertEquals(1.0, state.pose.forward dot -state.pose.position.normalized(), 1e-9) // still on the hole
    }

    @Test
    fun `the hole recoils against a turn and settles back`() {
        val scene = scene()
        scene.frame(0)
        scene.setGyroTarget(pitchDeg = 2.0, yawDeg = 4.0)

        var t = 0L
        var mostLeft = 0.0
        var mostDown = 0.0
        repeat(30) {
            t += frame
            val state = scene.frame(t)
            mostLeft = minOf(mostLeft, state.recoilX)
            mostDown = minOf(mostDown, state.recoilY)
        }
        val (settled, end) = scene.run(4.0, t)

        assertTrue(mostLeft < -0.01) // turning right, the hole goes left
        assertTrue(mostDown < -0.005) // turning up, it goes down
        assertTrue(abs(settled.recoilX) < 1e-3 && abs(settled.recoilY) < 1e-3)
        assertTrue(!scene.isActive(end + (InputParams().activityHoldSec * 1e9).toLong() + 1))
    }

    @Test
    fun `no recoil at strength 0 or for the preview's drag`() {
        val off = scene(InputParams(recoilStrength = 0.0))
        off.frame(0)
        off.setGyroTarget(pitchDeg = 0.0, yawDeg = 4.0)
        val drag = scene()
        drag.frame(0)
        drag.setOrbitOffset(elevationDeg = 5.0, azimuthDeg = 40.0)

        var t = 0L
        repeat(60) {
            t += frame
            val a = off.frame(t)
            val b = drag.frame(t)
            assertEquals(0.0, a.recoilX, 1e-12)
            assertEquals(0.0, b.recoilX, 1e-12)
            assertEquals(0.0, b.recoilY, 1e-12)
        }
    }

    @Test
    fun `the preview's drag turns the orbit about the disk axis`() {
        val scene = scene()
        scene.frame(0)
        scene.setOrbitOffset(elevationDeg = 5.0, azimuthDeg = 40.0)

        val state = scene.run(3.0).first

        assertEquals(15.0, state.elevationDeg, 1e-3)
        assertEquals(40.0, state.azimuthDeg, 1e-3)
    }
}
