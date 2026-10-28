package com.onthecrow.wallper.blackhole.model

import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.physics.StarMath

/** A slider of the settings screen and the setting it edits. */
internal enum class SettingsSlider(val range: ClosedFloatingPointRange<Double>) {
    ORBIT_PERIOD(BlackHoleSettings.ORBIT_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.orbitPeriodSec
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(orbitPeriodSec = value)
    },
    GYRO_SENSITIVITY(BlackHoleSettings.GYRO_SENSITIVITY_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.gyroSensitivity
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(gyroSensitivity = value)
    },
    GYRO_SMOOTHING(BlackHoleSettings.GYRO_SMOOTHING_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.gyroSmoothing
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(gyroSmoothing = value)
    },
    LAUNCHER_ANGLE(BlackHoleSettings.LAUNCHER_ANGLE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.launcherAngleDeg
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(launcherAngleDeg = value)
    },
    RESPONSE_SPEED(BlackHoleSettings.RESPONSE_SPEED_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.responseSpeed
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(responseSpeed = value)
    },
    RESPONSE_SMOOTHNESS(BlackHoleSettings.RESPONSE_SMOOTHNESS_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.responseSmoothness
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(responseSmoothness = value)
    },
    RECOIL_STRENGTH(BlackHoleSettings.RECOIL_STRENGTH_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.recoilStrength
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(recoilStrength = value)
    },
    RECOIL_RETURN(BlackHoleSettings.RECOIL_RETURN_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.recoilReturnSpeed
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(recoilReturnSpeed = value)
    },
    ELEVATION(BlackHoleSettings.ELEVATION_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.elevationDeg
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(elevationDeg = value)
    },
    DISK_ROLL(BlackHoleSettings.ROLL_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.diskRollDeg
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(diskRollDeg = value)
    },
    LENS_STRENGTH(BlackHoleSettings.LENS_STRENGTH_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.lensStrength
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(lensStrength = value)
    },
    LENS_FADE_START(BlackHoleSettings.LENS_FADE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.lensFadeStart
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(lensFadeStart = value)
    },
    LENS_FADE_END(BlackHoleSettings.LENS_FADE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.lensFadeEnd
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(lensFadeEnd = value)
    },
    RENDER_SCALE(BlackHoleSettings.RENDER_SCALE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.renderScale
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(renderScale = value)
    },
    DISK_BRIGHTNESS(BlackHoleSettings.DISK_BRIGHTNESS_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.diskBrightness
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(diskBrightness = value)
    },
    DISK_OPACITY(BlackHoleSettings.DISK_OPACITY_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.diskOpacity
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(diskOpacity = value)
    },
    BLOOM_INTENSITY(BlackHoleSettings.BLOOM_INTENSITY_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.bloomIntensity
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(bloomIntensity = value)
    },
    EXPOSURE(BlackHoleSettings.EXPOSURE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.exposure
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(exposure = value)
    },
    DISK_SPEED(BlackHoleSettings.DISK_SPEED_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.diskSpeed
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(diskSpeed = value)
    },
    STAR_COUNT(BlackHoleSettings.STAR_COUNT_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.starCount
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(starCount = value)
    },
    STAR_BRIGHTNESS(BlackHoleSettings.STAR_BRIGHTNESS_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.starBrightness
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(starBrightness = value)
    },
    /** A preset over size and widening: moving it sets both, the two sliders below fine-tune them. */
    STAR_SHARPNESS(BlackHoleSettings.STAR_SHARPNESS_RANGE) {
        override fun value(settings: BlackHoleSettings) = StarMath.sharpnessOf(settings.starSizePx)
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(
            starSizePx = StarMath.sizePx(value),
            starWidening = StarMath.footprintWidening(value),
        )
    },
    STAR_SIZE(BlackHoleSettings.STAR_SIZE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.starSizePx
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(starSizePx = value)
    },
    STAR_WIDENING(BlackHoleSettings.STAR_WIDENING_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.starWidening
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(starWidening = value)
    },
    FOV(BlackHoleSettings.FOV_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.fovDeg
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(fovDeg = value)
    },
    DISTANCE(BlackHoleSettings.DISTANCE_RANGE) {
        override fun value(settings: BlackHoleSettings) = settings.cameraDistance
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(cameraDistance = value)
    },
    DOPPLER_STRENGTH(0.0..1.0) {
        override fun value(settings: BlackHoleSettings) = settings.dopplerStrength
        override fun set(settings: BlackHoleSettings, value: Double) = settings.copy(dopplerStrength = value)
    };

    abstract fun value(settings: BlackHoleSettings): Double

    /** [settings] with this slider at [value], clamped to [range]. */
    fun with(settings: BlackHoleSettings, value: Double): BlackHoleSettings = set(settings, value.coerceIn(range))

    protected abstract fun set(settings: BlackHoleSettings, value: Double): BlackHoleSettings
}
