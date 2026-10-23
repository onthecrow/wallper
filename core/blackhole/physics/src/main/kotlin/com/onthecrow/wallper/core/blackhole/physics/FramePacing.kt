package com.onthecrow.wallper.core.blackhole.physics

import kotlin.math.max

enum class ThermalLevel { NORMAL, MODERATE, SEVERE }

/** Frame rate decisions (§11.4): never above the target, never above 60 on fast displays. */
object FramePacing {
    /** No continuous frames: one still frame, redrawn only when something changes. */
    const val STILL = 0

    fun targetFps(
        active: Boolean,
        powerSave: Boolean,
        thermal: ThermalLevel,
        frames: FrameParams,
        quality: QualityLevel,
    ): Int = when {
        thermal == ThermalLevel.SEVERE -> STILL
        powerSave -> if (frames.powerSaveStill) STILL else frames.fpsPowerSave
        active -> minOf(frames.fpsActive, quality.maxFpsActive, MAX_FPS)
        thermal == ThermalLevel.MODERATE -> minOf(frames.fpsIdle, quality.maxFpsIdle, MODERATE_IDLE_FPS)
        else -> minOf(frames.fpsIdle, quality.maxFpsIdle, MAX_FPS)
    }

    /**
     * Whether the GL thread should expect frames, which arms its heartbeat. From the paced rate, the same
     * on every API level: a still frame (heat, power save) expects none.
     */
    fun expectsFrames(visible: Boolean, targetFps: Int): Boolean = visible && targetFps != STILL

    /** Whether the vsync at [frameNanos] should be drawn; skips vsyncs on 90 and 120 Hz displays. */
    fun isDue(frameNanos: Long, lastDrawnNanos: Long, targetFps: Int, vsyncNanos: Long): Boolean {
        if (targetFps <= 0) return false
        if (lastDrawnNanos == Long.MIN_VALUE) return true
        val interval = 1_000_000_000L / targetFps
        return frameNanos - lastDrawnNanos >= max(0L, interval - vsyncNanos / 2)
    }

    private const val MAX_FPS = 60
    private const val MODERATE_IDLE_FPS = 24
}

/**
 * Automatic quality level: Auto starts on High and steps down while the GPU can't keep up.
 *
 * A GPU frame time alone says little: a GPU with headroom clocks down, so at 30 fps a fast phone takes as
 * long per frame as a slow one at full clock. What it can't hide is overload. The load, GPU time × frame
 * rate, stays above 1 only when even the top clock is too slow. When the median load of a measurement is
 * over [overload], the level steps down and the next measurement starts. It never steps up: a clocked-down
 * GPU hides its headroom. Without a GPU timer Auto stays on Medium (§12). Heat lowers the level by one only
 * while it lasts.
 *
 * A measurement skips the first frames of a visible stretch, which the unlock animation inflates. Hiding
 * the wallpaper or heating the GPU before it ends starts it over.
 */
class QualityController(
    initial: QualityLevel = QualityLevel.HIGH,
    private val sampleCount: Int = 120,
    private val warmupCount: Int = 60,
    private val overload: Double = 1.0,
) {
    /** The level of a cool device: the setting, or where the measurements put it. */
    private var base: QualityLevel = initial
    private var thermal = ThermalLevel.NORMAL

    val level: QualityLevel
        get() = if (thermal == ThermalLevel.NORMAL) base else base.lower()

    private val loads = ArrayList<Double>(sampleCount)
    private var skipped = 0
    private var settled = false

    /** Feeds the GPU time of a frame drawn at [fps]; returns the new level when it changes. */
    fun onGpuTime(ms: Double, fps: Int): QualityLevel? {
        // A still frame has no deadline, and a throttled GPU says nothing about the device.
        if (settled || fps <= 0 || thermal != ThermalLevel.NORMAL) return null
        if (skipped < warmupCount) {
            skipped++
            return null
        }
        loads += ms * fps / 1000.0
        if (loads.size < sampleCount) return null
        val median = loads.sorted()[loads.size / 2]
        restartMeasurement()
        if (median <= overload || base == QualityLevel.LOW) {
            settled = true
            return null
        }
        return changeLevel { base = base.lower() }
    }

    /** The driver can't time the GPU: nothing to measure, Medium is the safe level. */
    fun onNoGpuTimer(): QualityLevel? {
        if (settled) return null
        settled = true
        return changeLevel { base = minOf(base, QualityLevel.MEDIUM) }
    }

    /** Returns the new level when the heat changes it, either way. */
    fun onThermal(next: ThermalLevel): QualityLevel? = changeLevel {
        thermal = next
        if (next != ThermalLevel.NORMAL) restartMeasurement()
    }

    fun onHidden() = restartMeasurement()

    private fun restartMeasurement() {
        if (settled) return
        loads.clear()
        skipped = 0
    }

    private inline fun changeLevel(change: () -> Unit): QualityLevel? {
        val before = level
        change()
        return level.takeIf { it != before }
    }
}
