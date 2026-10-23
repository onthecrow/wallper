package com.onthecrow.wallper.core.engine.impl.engine

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
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleScene
import com.onthecrow.wallper.core.blackhole.physics.FramePacing
import com.onthecrow.wallper.core.blackhole.physics.GyroTracker
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualityController
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import com.onthecrow.wallper.core.blackhole.physics.QualitySetting
import com.onthecrow.wallper.core.blackhole.physics.Quaternion
import com.onthecrow.wallper.core.blackhole.physics.ThermalLevel
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleGlRenderer
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleRenderer
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettings
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettingsStore
import com.onthecrow.wallper.core.blackhole.renderer.LensTablesRepository
import com.onthecrow.wallper.core.blackhole.renderer.lensTableKey
import com.onthecrow.wallper.core.diagnostics.Diagnostics
import com.onthecrow.wallper.core.gl.WallpaperGLThread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The built-in black hole (BLACKHOLE_TZ.md §11): a [BlackHoleRenderer] on its own ES 3.0 GL thread, fed
 * by a CPU-side [BlackHoleScene]. Frames are paced by `Choreographer` on a callback thread: 0 fps when
 * hidden, idle and active rates in between, a still frame when the device is hot.
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
    private var tables: LensTables? = null
    private var tablesKey: LensTableKey? = null
    private var tablesJob: Job? = null
    private var visible = true
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
    /** Only to skip repeated `Surface.setFrameRate` calls (API 30+); [pacedFps] is what frames follow. */
    private var appliedFrameRate = -1
    @Volatile
    private var released = false
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

    private val gl = RestartableGl(
        holder = holder,
        handler = handler,
        onGiveUp = host::showPlaceholder,
        afterRestart = {
            stillDrawn = false
            wake()
        },
        newSession = { Gl(currentLevel()) },
    )

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
            loadTables()
            onVisible(true)
        }
        scope.launch { settingsStore.settings.collect(::onSettings) }
    }

    override fun pause() {
        handler.post { onVisible(false) }
    }

    override fun resume() {
        handler.post { onVisible(true) }
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
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        handler.post {
            scene.setLauncherOffset(xOffset, hasPages = xOffsetStep > 0f)
            wake()
        }
    }

    override fun onZoomChanged(zoom: Float) {
        handler.post {
            scene.setSystemZoom(zoom)
            wake()
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
        val current = gl.current
        val nextParams = params
        current.thread.post { current.renderer.setParams(nextParams) }
        if (visible && params.input.gyroEnabled) registerGyro() else unregisterGyro()
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
        val current = gl.current
        current.thread.post { current.renderer.onGpuTimeMs = if (timing) gpuTimeCallback else null }
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
        val current = gl.current
        // New shader defines: the next frame compiles them.
        gl.expectSlowFrame()
        current.thread.post { current.renderer.setQuality(level) }
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

    private fun loadTables() {
        val key = lensTableKey(params, currentLevel())
        if (key == tablesKey) return
        tablesKey = key
        tablesJob?.cancel()
        tablesJob = scope.launch {
            val loaded = tablesRepository.get(key)
            tables = loaded
            val current = gl.current
            current.thread.post { current.renderer.setTables(loaded) }
            stillDrawn = false
            wake()
        }
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
            }
            return // nothing more until something changes
        }
        if (FramePacing.isDue(frameNanos, lastDrawnNanos, target, vsyncNanos())) draw(frameNanos)
        // Gyro samples don't wake it early: a tilt shows from the next due frame on.
        scheduleFrame(FramePacing.sleepMs(frameNanos, lastDrawnNanos, target, vsyncNanos()))
    }

    private fun draw(frameNanos: Long) {
        val current = gl.current
        current.glRenderer.submit(scene.frame(frameNanos))
        current.thread.requestRender()
        lastDrawnNanos = frameNanos
    }

    private fun targetFps(nanos: Long): Int = FramePacing.targetFps(
        active = scene.isActive(nanos),
        powerSave = powerSave,
        thermal = thermal,
        frames = params.frames,
        quality = currentLevel(),
    )

    private fun applyFrameRate(target: Int) {
        if (target == appliedFrameRate || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        appliedFrameRate = target
        runCatching {
            holder.surface.setFrameRate(target.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    private fun updateFramesExpected() {
        gl.setFramesExpected(FramePacing.expectsFrames(visible, pacedFps))
    }

    private fun vsyncNanos(): Long = (1e9 / (display?.refreshRate?.takeIf { it > 0f } ?: 60f)).toLong()

    private fun registerGyro() {
        if (gyroRegistered || !visible || !params.input.gyroEnabled) return
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
        scheduleFrame()
    }

    /** One GL thread with its own renderer: a restart never shares GL state with an abandoned thread. */
    private inner class Gl(level: QualityLevel) : RestartableGl.Session {
        val renderer = BlackHoleRenderer(context.assets).apply {
            setQuality(level)
            setParams(params)
            tables?.let(::setTables)
            onGpuTimeMs = if (isMeasuringGpu()) gpuTimeCallback else null
            onNoGpuTimer = { handler.post { onNoGpuTimer() } }
        }
        val glRenderer = BlackHoleGlRenderer(renderer) { error -> gl.fail(this@Gl, error) }
        override val thread: WallpaperGLThread = WallpaperGLThread(
            surfaceHolder = holder,
            renderer = glRenderer,
            renderGuardListener = { stall -> gl.restart(this@Gl, "unrecoverable $stall") },
            glesVersion = 3,
            onInitFailed = { error -> gl.fail(this@Gl, error) },
        )
    }

    private companion object {
        const val TAG = "BlackHole"
        const val GYRO_PERIOD_US = 20_000
        const val STOP_TIMEOUT_MS = 1_500L
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
