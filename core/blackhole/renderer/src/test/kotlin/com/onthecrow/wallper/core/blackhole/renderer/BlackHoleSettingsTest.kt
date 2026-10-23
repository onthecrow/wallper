package com.onthecrow.wallper.core.blackhole.renderer

import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.InputParams
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import com.onthecrow.wallper.core.blackhole.physics.QualitySetting
import com.onthecrow.wallper.core.blackhole.physics.StarMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlackHoleSettingsTest {

    @Test
    fun `defaults keep the default parameters`() {
        assertEquals(BlackHoleParams(), BlackHoleSettings().applyTo(BlackHoleParams()))
    }

    @Test
    fun `settings override only their parameters`() {
        val params = BlackHoleSettings(
            quality = QualitySetting.LOW,
            gyroEnabled = false,
            orbitEnabled = false,
            elevationDeg = 12.0,
            cameraDistance = 40.0,
            starSeed = 7,
            idleFps = 24,
        ).applyTo(BlackHoleParams())

        assertEquals(QualitySetting.LOW, params.frames.quality)
        assertFalse(params.input.gyroEnabled)
        assertEquals(0.0, params.camera.orbitPeriodSec)
        assertEquals(12.0, params.camera.elevationDeg)
        assertEquals(40.0, params.camera.distance)
        assertEquals(7, params.stars.seed)
        assertEquals(24, params.frames.fpsIdle)
        assertEquals(BlackHoleParams().disk, params.disk)
    }

    @Test
    fun `response and recoil settings reach the input parameters`() {
        val input = BlackHoleSettings(
            gyroSensitivity = 1.0,
            gyroSmoothing = 0.0,
            launcherAngleDeg = 20.0,
            responseSpeed = 2.0,
            responseSmoothness = 0.5,
            recoilStrength = 0.0,
            recoilReturnSpeed = 2.0,
        ).applyTo(BlackHoleParams()).input
        val defaults = InputParams()

        assertEquals(1.0, input.gyroGain)
        assertEquals(0.0, input.gyroSmoothing)
        assertEquals(20.0, input.launcherAngleDeg)
        assertEquals(defaults.springOmega * 2, input.springOmega)
        assertEquals(0.5, input.springDamping)
        assertEquals(0.0, input.recoilStrength)
        assertEquals(defaults.recoilReturnOmega * 2, input.recoilReturnOmega)
    }

    @Test
    fun `star count, disk speed and roll reach their parameters`() {
        val params = BlackHoleSettings(starCount = 1.5, diskSpeed = 2.0, diskRollDeg = -20.0).applyTo(BlackHoleParams())

        assertEquals(1.5, params.stars.countScale)
        assertEquals(BlackHoleParams().disk.rotationPeriodSec / 2, params.disk.rotationPeriodSec)
        assertEquals(-20.0, params.camera.rollDeg)
    }

    @Test
    fun `sharper stars are smaller and widen less`() {
        val stars = BlackHoleSettings(starSizePx = 0.3, starWidening = 0.05).applyTo(BlackHoleParams()).stars
        val clamped = BlackHoleSettings(starSizePx = 9.0).applyTo(BlackHoleParams()).stars

        assertEquals(0.3, stars.sizePx)
        assertEquals(0.05, stars.footprintWidening)
        assertEquals(BlackHoleSettings.STAR_SIZE_RANGE.endInclusive, clamped.sizePx)
        assertTrue(StarMath.sizePx(1.0) < StarMath.sizePx(0.0))
    }

    @Test
    fun `the field of view reaches the camera`() {
        assertEquals(40.0, BlackHoleSettings(fovDeg = 40.0).applyTo(BlackHoleParams()).camera.fovYDeg)
        assertEquals(BlackHoleSettings.FOV_RANGE.start, BlackHoleSettings(fovDeg = 5.0).applyTo(BlackHoleParams()).camera.fovYDeg)
    }

    @Test
    fun `the lens strength reaches the sky lensing and stays in range`() {
        assertEquals(0.3, BlackHoleSettings(lensStrength = 0.3).applyTo(BlackHoleParams()).lens.strength)
        assertEquals(2.0, BlackHoleSettings(lensStrength = 5.0).applyTo(BlackHoleParams()).lens.strength)
    }

    @Test
    fun `picture settings reach their parameters`() {
        val params = BlackHoleSettings(
            diskEnabled = false,
            diskBrightness = 2.0,
            diskOpacity = 0.5,
            starsEnabled = false,
            hazeEnabled = false,
            bloomEnabled = false,
            bloomIntensity = 1.2,
            exposure = 9.0,
            zoomOutEnabled = false,
            diskOpacityFalloff = false,
        ).applyTo(BlackHoleParams())

        assertFalse(params.disk.enabled)
        assertEquals(2.0, params.disk.brightness)
        assertEquals(0.5, params.disk.opacity)
        assertFalse(params.stars.enabled)
        assertFalse(params.haze.enabled)
        assertFalse(params.post.bloomEnabled)
        assertEquals(1.2, params.post.bloomIntensity)
        assertEquals(BlackHoleSettings.EXPOSURE_RANGE.endInclusive, params.post.exposure)
        assertEquals(0.0, params.camera.zoomOutFov)
        assertFalse(params.disk.opacityFalloffEnabled)
    }

    @Test
    fun `speed zero stops the disk`() {
        val params = BlackHoleSettings(diskSpeed = 0.0).applyTo(BlackHoleParams())

        assertEquals(0.0, params.disk.rotationPeriodSec)
    }

    @Test
    fun `the camera never gets into the disk`() {
        val params = BlackHoleSettings(cameraDistance = 5.0).applyTo(BlackHoleParams())

        assertEquals(BlackHoleSettings.DISTANCE_RANGE.start, params.camera.distance)
    }

    @Test
    fun `table keys follow the distance and the quality`() {
        val params = BlackHoleSettings(cameraDistance = 40.0).applyTo(BlackHoleParams())

        val key = lensTableKey(params, QualityLevel.LOW)

        assertEquals(40.0, key.cameraDistance)
        assertEquals(512, key.nAlpha)
        assertEquals(512, key.nPhi)
    }
}
