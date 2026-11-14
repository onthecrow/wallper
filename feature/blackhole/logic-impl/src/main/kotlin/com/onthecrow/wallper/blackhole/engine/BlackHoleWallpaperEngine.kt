package com.onthecrow.wallper.blackhole.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.Choreographer
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.onthecrow.wallper.blackhole.data.AutoQualityMemory
import com.onthecrow.wallper.blackhole.data.BlackHoleSettingsStore
import com.onthecrow.wallper.blackhole.data.LensTablesRepository
import com.onthecrow.wallper.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.blackhole.physics.BlackHoleScene
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.physics.FramePacing
import com.onthecrow.wallper.blackhole.physics.GyroTracker
import com.onthecrow.wallper.blackhole.physics.LensTableKey
import com.onthecrow.wallper.blackhole.physics.QualityController
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.physics.QualitySetting
import com.onthecrow.wallper.blackhole.physics.Quaternion
import com.onthecrow.wallper.blackhole.physics.ThermalLevel
import com.onthecrow.wallper.blackhole.physics.lensTableKey
import com.onthecrow.wallper.blackhole.renderer.BlackHoleGlRenderer
import com.onthecrow.wallper.blackhole.renderer.BlackHoleRenderer
import com.onthecrow.wallper.core.diagnostics.Diagnostics
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.SlideStart
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.gl.GlClient
import com.onthecrow.wallper.core.gl.GlLink
import com.onthecrow.wallper.core.gl.OwnGl
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * The built-in black hole (BLACKHOLE_TZ.md §11): a [BlackHoleRenderer] on its own ES 3.0 GL thread, fed
 * by a CPU-side [BlackHoleScene]. Frames are paced by `Choreographer` on a callback thread: 0 fps when
 * hidden, idle and active rates in between, a still frame when the device is hot.
 *
 * Hidden, it does no work at all: no frames, no gyroscope, no GL heartbeat, and new lens tables wait
 * until it shows again. Only listeners that cost nothing until they fire stay registered.
 *
 * As a series' [slide] it starts hidden, on the GL host it is given, and warms up: it loads its lens tables
 * and draws off screen until the first frame with the picture, which builds the shaders. From then on it
 * is ready to [show], and it appears whole, without the fade from black.
 *
 * Everything except the [WallpaperEngine] overrides runs on the callback thread.
 */
internal class BlackHoleWallpaperEngine(
    private val context: Context,
    surfaceHolder: () -> SurfaceHolder,
    private val isPreview: Boolean,
    private val host: EngineHost,
    private val settingsStore: BlackHoleSettingsStore,
    private val tablesRepository: LensTablesRepository,
    private val autoQuality: AutoQualityMemory,
    private val slide: SlideStart? = null,
) : WallpaperEngine() {

    private val holder = surfaceHolder()

    private val callbackThread = HandlerThread("BlackHoleFrames").apply { start() }
    private val handler = Handler(callbackThread.looper)

    // What escapes (the tables out of memory, a settings bug) ends in the placeholder, not in a crash of
    // the process that draws the wallpaper.
    private val scope = CoroutineScope(
        SupervisorJob() + handler.asCoroutineDispatcher("BlackHoleFrames") +
            CoroutineExceptionHandler { _, error -> host.showPlaceholder("The black hole failed", error) },
    )

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val display: Display? = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Callback thread state.
    private var settings: BlackHoleSettings = settingsStore.settings.value
    private var params: BlackHoleParams = settings.applyTo(BlackHoleParams())
    private val scene = BlackHoleScene(params)
    private val gyro = GyroTracker()
    private var quality: QualityController = newQualityController(settings)
    private val gpuTimeCallback: (Double) -> Unit = { ms -> handler.post { onGpuTime(ms) } }
    private val rotationQuaternion = FloatArray(4)
    private var tablesKey: LensTableKey? = null
    private var tablesJob: Job? = null
    private var visible = slide == null

    /** A slide made off screen, until its first picture: hidden, yet it loads its tables and draws for it. */
    private var warming = slide != null
    private var powerSave = powerManager?.isPowerSaveMode == true
    private var thermal = ThermalLevel.NORMAL
    private var lastDrawnNanos = Long.MIN_VALUE
    /** The rate frames are paced at now, which their GPU time is held against; 0 for a still frame. */
    private var pacedFps = FramePacing.STILL
    private var frameScheduled = false
    /** The pending frame callback ends a sleep between frames: [wake] may cut it short. */
    private var frameDelayed = false
    private var stillDrawn = false
    private var lastSensorNanos = 0L
    private var gyroRegistered = false
    /** The gyroscope target the last frame drew; a still frame redraws once the target leaves it. */
    private var drawnGyroPitchDeg = 0.0
    private var drawnGyroYawDeg = 0.0
    /** Only to skip repeated `Surface.setFrameRate` calls (API 30+); [pacedFps] is what frames follow. */
    private var appliedFrameRate = -1
    @Volatile
    private var released = false

    /** A slide's window went on to the next slide: its frame rate is that one's to ask for. */
    @Volatile
    private var windowHandedOver = false
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() } // the callback looper's
    private val frameCallback = Choreographer.FrameCallback(::onVsync)


    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = onRotation(event)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            powerSave = powerManager?.isPowerSaveMode == true
            Timber.tag(TAG).d("Power save: $powerSave")
            stillDrawn = false
            wake()
        }
    }

    // Typed as Any: the listener interface doesn't exist before API 29.
    private val thermalListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener(::onThermalStatus)
    } else {
        null
    }

    private val gl: GlLink<BlackHoleGlRenderer> = (slide?.gl ?: OwnGl(holder)).open(handler, Renderers())

    init {
        Timber.tag(TAG).d("created, preview=$isPreview")
        reportQuality(currentLevel())
        gl.start()
        handler.post {
            ContextCompat.registerReceiver(
                context, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                null, handler, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val listener = thermalListener as PowerManager.OnThermalStatusChangedListener
                powerManager?.addThermalStatusListener({ handler.post(it) }, listener)
                powerManager?.currentThermalStatus?.let(::onThermalStatus)
            }
            if (warming) warmUp() else onVisible(true)
        }
        scope.launch { settingsStore.settings.collect(::onSettings) }
    }

    override fun pause() {
        handler.post { onVisible(false) }
    }

    override fun resume() {
        handler.post { onVisible(true) }
    }

    override fun show() {
        handler.post {
            // The frame warmed up with is as old as the warm-up: the disk would jump to where it is now.
            draw(System.nanoTime())
            gl.show()
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        gl.onSurfaceChanged(width, height)
        handler.post {
            stillDrawn = false
            wake()
        }
    }

    // The system frees the window right after this returns: wait for the EGL surface to go.
    override fun onSurfaceDestroyed() {
        gl.onSurfaceDestroyed(SURFACE_DESTROY_WAIT_MS)
        if (slide != null) {
            // Now, not when this engine is released: by then the next slide may have asked for its own rate.
            windowHandedOver = true
            setFrameRate(NO_FRAME_RATE)
        }
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        handler.post {
            scene.setLauncherOffset(xOffset, hasPages = xOffsetStep > 0f)
            onInputMoved()
        }
    }

    override fun onZoomChanged(zoom: Float) {
        handler.post {
            scene.setSystemZoom(zoom)
            onInputMoved()
        }
    }

    override fun release() {
        Timber.tag(TAG).d("released")
        released = true
        handler.post {
            scope.cancel()
            choreographer.removeFrameCallback(frameCallback)
            unregisterGyro()
            runCatching { context.unregisterReceiver(powerReceiver) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager?.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
            }
            // Slides that share a GL thread hand no window over: the next one may never say what rate it wants.
            if (slide != null) applyFrameRate(NO_FRAME_RATE)
        }
        // Runs after the teardown above on the callback thread, and waits for both.
        gl.release(STOP_TIMEOUT_MS)
        callbackThread.quitSafely()
    }

    // ---------- callback thread ----------

    private fun onVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) {
            gyro.reset()
            scene.resetInputs()
            registerGyro()
            gl.setVisible(true)
            loadTables() // what the heat or the settings changed while hidden
            stillDrawn = false
            lastDrawnNanos = Long.MIN_VALUE
            draw(System.nanoTime()) // a fresh frame right away, never a stale one
            scheduleFrame()
        } else {
            quality.onHidden()
            unregisterGyro()
            choreographer.removeFrameCallback(frameCallback)
            frameScheduled = false
            gl.setVisible(false)
        }
        updateFramesExpected()
    }

    private fun onSettings(next: BlackHoleSettings) {
        val qualityChanged = next.quality != settings.quality
        // Other shader defines: the next frame compiles them.
        if (next.optimizedRendering != settings.optimizedRendering) gl.expectSlowFrame()
        settings = next
        params = next.applyTo(BlackHoleParams())
        scene.params = params
        if (qualityChanged) {
            // Picking Auto again is the way to measure from High once more.
            if (next.quality == QualitySetting.AUTO && !isPreview) autoQuality.clear()
            quality = newQualityController(next).apply { onThermal(thermal) }
            applyQuality()
            updateGpuTiming()
        }
        val nextParams = params
        gl.post { it.renderer.setParams(nextParams) }
        updateGyro()
        loadTables()
        stillDrawn = false
        wake()
    }

    private fun onThermalStatus(status: Int) {
        thermal = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> ThermalLevel.NORMAL
            status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            else -> ThermalLevel.NORMAL
        }
        Timber.tag(TAG).i("Thermal: $thermal")
        quality.onThermal(thermal)?.let { level ->
            Timber.tag(TAG).i("Quality: $level while $thermal")
            applyQuality()
        }
        updateGyro()
        stillDrawn = false
        updateFramesExpected()
        wake()
    }

    // GPU times arrive a few frames late; a change of pace in between barely moves the median.
    private fun onGpuTime(ms: Double) {
        if (!isMeasuringGpu()) return
        quality.onGpuTime(ms, pacedFps)?.let { level ->
            Timber.tag(TAG).i("Quality: $level, the GPU didn't keep up")
            applyQuality()
        }
        if (!quality.isMeasuring) {
            autoQuality.save(quality.level)
            updateGpuTiming()
        }
    }

    private fun isMeasuringGpu(): Boolean = !isPreview && settings.quality == QualitySetting.AUTO && quality.isMeasuring

    /** The GPU timer runs only while Auto measures: its queries cost a little on every frame. */
    private fun updateGpuTiming() {
        val timing = isMeasuringGpu()
        gl.post { it.renderer.onGpuTimeMs = if (timing) gpuTimeCallback else null }
    }

    private fun onNoGpuTimer() {
        if (isPreview || settings.quality != QualitySetting.AUTO) return
        quality.onNoGpuTimer()?.let { level ->
            Timber.tag(TAG).i("Quality: $level, no GPU timer")
            applyQuality()
        }
    }

    private fun applyQuality() {
        val level = currentLevel()
        reportQuality(level)
        // New shader defines: the next frame compiles them.
        gl.expectSlowFrame()
        gl.post { it.renderer.setQuality(level) }
        loadTables()
    }

    /** The level reports come with: the wallpaper's, not the system picker's cheap preview. */
    private fun reportQuality(level: QualityLevel) {
        if (!isPreview) Diagnostics.reporter.setKey("black_hole_quality", level.name)
    }

    private fun currentLevel(): QualityLevel = if (isPreview) QualityLevel.LOW else quality.level

    /** Auto starts where it settled last time on this device, and still steps down if it has to. */
    private fun newQualityController(settings: BlackHoleSettings): QualityController {
        val remembered = if (settings.quality == QualitySetting.AUTO && !isPreview) autoQuality.load() else null
        return QualityController(remembered ?: settings.quality.startLevel)
    }

    /**
     * Reading or generating tables takes up to seconds of every core, and they wait in memory for the
     * next frame: a hidden wallpaper leaves that to [onVisible]. The GPU keeps the old ones until then.
     */
    private fun loadTables() {
        if (!visible && !warming) return
        val key = lensTableKey(params, currentLevel())
        if (key == tablesKey) return
        tablesKey = key
        tablesJob?.cancel()
        tablesJob = scope.launch {
            val loaded = tablesRepository.get(key)
            gl.post { it.renderer.setTables(loaded) }
            stillDrawn = false
            // Hidden, no frame is coming: a slide that warms up draws the one that has its picture.
            if (visible) wake() else if (warming) draw(System.nanoTime())
        }
    }

    /** A slide made off screen: no frame loop and no gyroscope yet, only what the first picture needs. */
    private fun warmUp() {
        loadTables()
        // Without the tables this frame is black, but it builds the shaders meanwhile.
        draw(System.nanoTime())
    }

    private fun reloadTables() {
        tablesKey = null
        loadTables()
    }

    /** A frame callback after [delayMs], or the next vsync; nothing if one is already coming. */
    private fun scheduleFrame(delayMs: Long = 0) {
        if (!visible || frameScheduled || released) return
        frameScheduled = true
        frameDelayed = delayMs > 0
        if (frameDelayed) {
            choreographer.postFrameCallbackDelayed(frameCallback, delayMs)
        } else {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /** Something on screen changed: draw at the next vsync rather than when the sleep between frames ends. */
    private fun wake() {
        if (frameScheduled && frameDelayed) {
            choreographer.removeFrameCallback(frameCallback)
            frameScheduled = false
        }
        scheduleFrame()
    }

    private fun onVsync(frameNanos: Long) {
        frameScheduled = false
        if (!visible) return
        val target = targetFps(frameNanos)
        if (target != pacedFps) {
            pacedFps = target
            updateFramesExpected()
        }
        applyFrameRate(target)
        if (target == FramePacing.STILL) {
            if (!stillDrawn) {
                draw(frameNanos)
                stillDrawn = true
                // The frame noticed motion (a tilt, a swipe): the power-save parallax takes over.
                if (scene.isActive(frameNanos)) scheduleFrame()
            }
            return // nothing more until something changes
        }
        if (FramePacing.isDue(frameNanos, lastDrawnNanos, target, vsyncNanos())) {
            draw(frameNanos)
            // When the motion stops, one more still frame shows where it came to rest.
            stillDrawn = false
        }
        // Gyro samples don't wake it early: a tilt shows from the next due frame on.
        scheduleFrame(FramePacing.sleepMs(frameNanos, lastDrawnNanos, target, vsyncNanos()))
    }

    private fun draw(frameNanos: Long) {
        gl.renderer.submit(scene.frame(frameNanos))
        gl.requestRender()
        lastDrawnNanos = frameNanos
        drawnGyroPitchDeg = gyro.targetPitchDeg
        drawnGyroYawDeg = gyro.targetYawDeg
    }

    /** A launcher swipe, a zoom or a tilt: a still frame redraws, unless the heat keeps the picture still. */
    private fun onInputMoved() {
        if (thermal != ThermalLevel.SEVERE) stillDrawn = false
        wake()
    }

    private fun targetFps(nanos: Long): Int = FramePacing.targetFps(
        active = scene.isActive(nanos),
        powerSave = powerSave,
        thermal = thermal,
        frames = params.frames,
        quality = currentLevel(),
    )

    private fun applyFrameRate(target: Int) {
        if (target == appliedFrameRate || windowHandedOver) return
        appliedFrameRate = target
        setFrameRate(target)
    }

    /** Any thread. */
    private fun setFrameRate(target: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            holder.surface.setFrameRate(target.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    private fun updateFramesExpected() {
        gl.setFramesExpected(FramePacing.expectsFrames(visible, pacedFps))
    }

    private fun vsyncNanos(): Long = (1e9 / (display?.refreshRate?.takeIf { it > 0f } ?: 60f)).toLong()

    /** Under severe heat the picture stays still whatever the phone does: no samples to wake for. */
    private fun gyroWanted() = visible && params.input.gyroEnabled && thermal != ThermalLevel.SEVERE

    private fun updateGyro() = if (gyroWanted()) registerGyro() else unregisterGyro()

    private fun registerGyro() {
        if (gyroRegistered || !gyroWanted()) return
        val sensor = rotationSensor ?: return
        gyroRegistered = sensorManager?.registerListener(sensorListener, sensor, GYRO_PERIOD_US, handler) == true
        lastSensorNanos = 0L
    }

    private fun unregisterGyro() {
        if (!gyroRegistered) return
        sensorManager?.unregisterListener(sensorListener)
        gyroRegistered = false
    }

    private fun onRotation(event: SensorEvent) {
        val q = rotationQuaternion
        SensorManager.getQuaternionFromVector(q, event.values) // w, x, y, z
        val dt = if (lastSensorNanos == 0L) 0.0 else (event.timestamp - lastSensorNanos) / 1e9
        lastSensorNanos = event.timestamp
        val rotation = when (display?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        gyro.onSample(Quaternion(q[1].toDouble(), q[2].toDouble(), q[3].toDouble(), q[0].toDouble()), dt, rotation, params.input)
        scene.setGyroTarget(gyro.targetPitchDeg, gyro.targetYawDeg)
        if (pacedFps != FramePacing.STILL) {
            scheduleFrame()
        } else if (abs(gyro.targetPitchDeg - drawnGyroPitchDeg) > STILL_WAKE_DEG ||
            abs(gyro.targetYawDeg - drawnGyroYawDeg) > STILL_WAKE_DEG
        ) {
            // A still frame doesn't follow every sample, only a tilt that moves the picture.
            onInputMoved()
        }
    }

    /** What the GL side asks of this engine: a renderer of its own for each GL thread, as the engine is set now. */
    private inner class Renderers : GlClient<BlackHoleGlRenderer> {
        override val glesVersion = 3

        override fun newRenderer(): BlackHoleGlRenderer {
            lateinit var glRenderer: BlackHoleGlRenderer
            val renderer = BlackHoleRenderer(context.assets).apply {
                // A slide takes over from another picture: it comes whole, not out of black.
                fadeIn = slide == null
                setQuality(currentLevel())
                setParams(params)
                onTablesLost = { handler.post { if (gl.isCurrent(glRenderer)) reloadTables() } }
                onGpuTimeMs = if (isMeasuringGpu()) gpuTimeCallback else null
                onNoGpuTimer = { handler.post { onNoGpuTimer() } }
            }
            glRenderer = BlackHoleGlRenderer(renderer) { error -> gl.fail(glRenderer, error) }
            return glRenderer
        }

        override fun afterRestart() {
            // A new renderer starts without tables; nothing keeps a CPU copy of them.
            reloadTables()
            stillDrawn = false
            wake()
        }

        override fun onFirstPicture() {
            warming = false
            slide?.onReady?.invoke()
        }

        override fun onGiveUp(reason: String, error: Throwable?) = host.showPlaceholder(reason, error)
    }

    private companion object {
        const val TAG = "BlackHole"
        const val GYRO_PERIOD_US = 20_000

        /** About a pixel at the default field of view. */
        const val STILL_WAKE_DEG = 0.03
        const val STOP_TIMEOUT_MS = 1_500L
        const val SURFACE_DESTROY_WAIT_MS = 1_000L

        /** `Surface.setFrameRate`'s «no preference». */
        const val NO_FRAME_RATE = 0
    }
}
