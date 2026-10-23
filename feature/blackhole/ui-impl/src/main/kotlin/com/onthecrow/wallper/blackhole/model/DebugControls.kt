package com.onthecrow.wallper.blackhole.model

import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleSettings

/**
 * A slider of the debug screen that edits one of [BlackHoleParams] and needs nothing else: unlike the
 * camera distance, none of them changes the lens tables. Debug edits stay on the debug screen. A slider
 * with a user setting behind it has that setting's range.
 */
internal enum class DebugSlider(val range: ClosedFloatingPointRange<Double>) {
    FOV(BlackHoleSettings.FOV_RANGE) {
        override fun value(params: BlackHoleParams) = params.camera.fovYDeg
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(camera = params.camera.copy(fovYDeg = value))
    },
    DISK_BRIGHTNESS(BlackHoleSettings.DISK_BRIGHTNESS_RANGE) {
        override fun value(params: BlackHoleParams) = params.disk.brightness
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(disk = params.disk.copy(brightness = value))
    },
    DISK_OPACITY(BlackHoleSettings.DISK_OPACITY_RANGE) {
        override fun value(params: BlackHoleParams) = params.disk.opacity
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(disk = params.disk.copy(opacity = value))
    },

    /** k in opacity × (R_IN / r)^k; works while [DebugToggle.OPACITY_FALLOFF] is on. */
    DISK_OPACITY_FALLOFF(0.0..4.0) {
        override fun value(params: BlackHoleParams) = params.disk.opacityFalloffPower
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(disk = params.disk.copy(opacityFalloffPower = value))
    },
    DISK_ROLL(BlackHoleSettings.ROLL_RANGE) {
        override fun value(params: BlackHoleParams) = params.camera.rollDeg
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(camera = params.camera.copy(rollDeg = value))
    },

    /** One turn of the inner edge; 0 stops the disk. */
    DISK_ROTATION_PERIOD(0.0..160.0) {
        override fun value(params: BlackHoleParams) = params.disk.rotationPeriodSec
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(disk = params.disk.copy(rotationPeriodSec = value))
    },
    BLOOM_INTENSITY(BlackHoleSettings.BLOOM_INTENSITY_RANGE) {
        override fun value(params: BlackHoleParams) = params.post.bloomIntensity
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(post = params.post.copy(bloomIntensity = value))
    },
    EXPOSURE(BlackHoleSettings.EXPOSURE_RANGE) {
        override fun value(params: BlackHoleParams) = params.post.exposure
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(post = params.post.copy(exposure = value))
    },
    LENS_STRENGTH(BlackHoleSettings.LENS_STRENGTH_RANGE) {
        override fun value(params: BlackHoleParams) = params.lens.strength
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(lens = params.lens.copy(strength = value))
    },
    STAR_COUNT(BlackHoleSettings.STAR_COUNT_RANGE) {
        override fun value(params: BlackHoleParams) = params.stars.countScale
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(stars = params.stars.copy(countScale = value))
    },
    STAR_SIZE(BlackHoleSettings.STAR_SIZE_RANGE) {
        override fun value(params: BlackHoleParams) = params.stars.sizePx
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(stars = params.stars.copy(sizePx = value))
    },
    STAR_WIDENING(BlackHoleSettings.STAR_WIDENING_RANGE) {
        override fun value(params: BlackHoleParams) = params.stars.footprintWidening
        override fun set(params: BlackHoleParams, value: Double) =
            params.copy(stars = params.stars.copy(footprintWidening = value))
    };

    abstract fun value(params: BlackHoleParams): Double

    /** [params] with this slider at [value], clamped to [range]. */
    fun with(params: BlackHoleParams, value: Double): BlackHoleParams = set(params, value.coerceIn(range))

    protected abstract fun set(params: BlackHoleParams, value: Double): BlackHoleParams
}

/** An on/off switch of the debug screen inside [BlackHoleParams]. */
internal enum class DebugToggle {
    DISK {
        override fun isOn(params: BlackHoleParams) = params.disk.enabled
        override fun with(params: BlackHoleParams, on: Boolean) = params.copy(disk = params.disk.copy(enabled = on))
    },
    STARS {
        override fun isOn(params: BlackHoleParams) = params.stars.enabled
        override fun with(params: BlackHoleParams, on: Boolean) = params.copy(stars = params.stars.copy(enabled = on))
    },
    HAZE {
        override fun isOn(params: BlackHoleParams) = params.haze.enabled
        override fun with(params: BlackHoleParams, on: Boolean) = params.copy(haze = params.haze.copy(enabled = on))
    },
    BLOOM {
        override fun isOn(params: BlackHoleParams) = params.post.bloomEnabled
        override fun with(params: BlackHoleParams, on: Boolean) = params.copy(post = params.post.copy(bloomEnabled = on))
    },
    DOPPLER {
        override fun isOn(params: BlackHoleParams) = params.doppler.enabled
        override fun with(params: BlackHoleParams, on: Boolean) = params.copy(doppler = params.doppler.copy(enabled = on))
    },
    OPACITY_FALLOFF {
        override fun isOn(params: BlackHoleParams) = params.disk.opacityFalloffEnabled
        override fun with(params: BlackHoleParams, on: Boolean) =
            params.copy(disk = params.disk.copy(opacityFalloffEnabled = on))
    };

    abstract fun isOn(params: BlackHoleParams): Boolean
    abstract fun with(params: BlackHoleParams, on: Boolean): BlackHoleParams
}
