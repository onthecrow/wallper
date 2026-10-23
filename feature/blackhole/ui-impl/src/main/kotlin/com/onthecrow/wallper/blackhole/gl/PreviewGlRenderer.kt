package com.onthecrow.wallper.blackhole.gl

import android.content.res.AssetManager
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleScene
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tanh

/**
 * [BlackHoleRenderer] and [BlackHoleScene] behind a [GLSurfaceView.Renderer].
 *
 * Every member runs on the GL thread: the renderer callbacks by contract, [setConfig], [setGyroTarget]
 * and [orbitBy] through [GLSurfaceView.queueEvent]. Queued events also run while the view is paused
 * and has no context, so they only record what to draw; the renderer picks it up at the next frame,
 * when a context is current.
 *
 * @param onStats FPS and the mean GPU time (null when the driver can't measure it), about twice a second.
 * @param onFailure once, when the shaders don't build.
 */
internal class PreviewGlRenderer(
    assets: AssetManager,
    private val onStats: (fps: Double, gpuMs: Double?) -> Unit,
    private val onFailure: (String) -> Unit,
) : GLSurfaceView.Renderer {

    private val scene = BlackHoleScene()
    private val renderer = BlackHoleRenderer(assets)

    private var config: PreviewConfig? = null
    private var isConfigDirty = false
    private var appliedTables: LensTables? = null
    private var hadContext = false
    private var failureReported = false

    // The orbit the user dragged to; it stays after the finger lifts.
    private var orbitElevationDeg = 0.0
    private var orbitAzimuthDeg = 0.0

    // Scene time: System.nanoTime() minus the time spent frozen.
    private var frozenAtNanos: Long? = null
    private var frozenTotalNanos = 0L

    private var statsStartNanos = 0L
    private var statsFrames = 0
    private var gpuSumMs = 0.0
    private var gpuSamples = 0

    init {
        // Set before the first frame: the renderer creates its GPU timer only if someone listens.
        renderer.onGpuTimeMs = { ms ->
            gpuSumMs += ms
            gpuSamples++
        }
    }

    fun setConfig(config: PreviewConfig) {
        if (config == this.config) return
        this.config = config
        isConfigDirty = true
    }

    /** The gyroscope, real or emulated by a drag, in the picture's axes. The emulated one is soft-clamped like the real one. */
    fun setGyroTarget(pitchDeg: Double, yawDeg: Double, clamp: Boolean = false) {
        val limit = if (clamp) scene.params.input.gyroMaxAngleDeg else Double.POSITIVE_INFINITY
        scene.setGyroTarget(softClamp(pitchDeg, limit), softClamp(yawDeg, limit))
    }

    /** Turns the orbit by a drag step; the scene still clamps the elevation to its limits. */
    fun orbitBy(elevationDeg: Double, azimuthDeg: Double) {
        orbitElevationDeg = (orbitElevationDeg + elevationDeg).coerceIn(-ORBIT_ELEVATION_LIMIT, ORBIT_ELEVATION_LIMIT)
        orbitAzimuthDeg += azimuthDeg // unbounded on purpose: a wrap would send the spring the long way round
        scene.setOrbitOffset(orbitElevationDeg, orbitAzimuthDeg)
    }

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        // GLSurfaceView makes a new context after a pause: the old GPU names died with the previous one.
        if (hadContext) renderer.onContextLost()
        hadContext = true
        appliedTables = null
        isConfigDirty = true
        applyConfig()
        resetStats()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = renderer.onSurfaceSize(width, height)

    override fun onDrawFrame(gl: GL10?) {
        applyConfig()
        renderer.render(scene.frame(sceneNanos()), SystemClock.elapsedRealtimeNanos())
        reportFailure()
        countFrame()
    }

    private fun applyConfig() {
        val config = config ?: return
        if (!isConfigDirty) return
        isConfigDirty = false
        renderer.setQuality(config.quality)
        renderer.setParams(config.params)
        renderer.setDebug(config.debugOptions)
        val tables = config.tables
        if (tables != null && tables !== appliedTables) {
            renderer.setTables(tables)
            appliedTables = tables
        }
        // The view feeds the gyroscope only when it should (a drag on the debug screen, the sensor when
        // the setting is on), so the switch itself stays out of the way.
        scene.params = config.params.copy(input = config.params.input.copy(gyroEnabled = true))
        scene.manualElevationDeg = config.manualElevationDeg
        scene.manualAzimuthDeg = config.manualAzimuthDeg
        scene.setOrbitOffset(orbitElevationDeg, orbitAzimuthDeg)
        freezeTime(config.isTimeFrozen)
    }

    private fun freezeTime(frozen: Boolean) {
        val now = System.nanoTime()
        val frozenAt = frozenAtNanos
        if (frozen && frozenAt == null) {
            frozenAtNanos = now - frozenTotalNanos
        } else if (!frozen && frozenAt != null) {
            // Carry on from the frozen moment instead of jumping ahead.
            frozenTotalNanos = now - frozenAt
            frozenAtNanos = null
        }
    }

    private fun sceneNanos(): Long = frozenAtNanos ?: (System.nanoTime() - frozenTotalNanos)

    private fun reportFailure() {
        val failure = renderer.failure ?: return
        if (failureReported) return
        failureReported = true
        onFailure(failure.message ?: failure.toString())
    }

    private fun countFrame() {
        val now = System.nanoTime()
        statsFrames++
        val elapsed = now - statsStartNanos
        if (elapsed < STATS_INTERVAL_NANOS) return
        // A longer gap means the view was paused: that window says nothing about the frame rate.
        if (elapsed < 2 * STATS_INTERVAL_NANOS) {
            onStats(statsFrames * 1e9 / elapsed, if (gpuSamples > 0) gpuSumMs / gpuSamples else null)
        }
        resetStats(now)
    }

    private fun resetStats(now: Long = System.nanoTime()) {
        statsStartNanos = now
        statsFrames = 0
        gpuSumMs = 0.0
        gpuSamples = 0
    }

    private fun softClamp(value: Double, limit: Double) = when {
        limit == Double.POSITIVE_INFINITY -> value
        limit > 0 -> limit * tanh(value / limit)
        else -> 0.0
    }

    private companion object {
        const val STATS_INTERVAL_NANOS = 500_000_000L
        const val ORBIT_ELEVATION_LIMIT = 60.0
    }
}
