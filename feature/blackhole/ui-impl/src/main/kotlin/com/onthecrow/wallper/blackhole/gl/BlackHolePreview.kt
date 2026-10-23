package com.onthecrow.wallper.blackhole.gl

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.Choreographer
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.core.blackhole.physics.FramePacing

/**
 * An ES 3.0 [GLSurfaceView] drawing [config] at most [PREVIEW_FPS] times a second, the wallpaper's rate
 * while it moves: not at the display's 90–120 Hz, so the stats show what the wallpaper costs and the
 * settings screen doesn't heat the phone. It pauses with the lifecycle, which drops
 * the EGL context; [PreviewGlRenderer] rebuilds its GPU objects on the next one. A drag on the picture
 * moves the camera as [dragMode] says; with [followsGyroscope] tilting the phone turns it like on the wallpaper.
 *
 * @param onStats and [onFailure] are called on the GL thread.
 */
@Composable
internal fun BlackHolePreview(
    config: PreviewConfig,
    dragMode: DragMode,
    onStats: (fps: Double, gpuMs: Double?) -> Unit,
    onFailure: (String) -> Unit,
    modifier: Modifier = Modifier,
    followsGyroscope: Boolean = false,
) {
    val context = LocalContext.current
    val currentOnStats by rememberUpdatedState(onStats)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val renderer = remember {
        PreviewGlRenderer(
            assets = context.assets,
            onStats = { fps, gpuMs -> currentOnStats(fps, gpuMs) },
            onFailure = { currentOnFailure(it) },
        )
    }
    // The view belongs to the AndroidView node: a new node gets a new view, and the renderer takes the
    // new context like after a pause.
    var view by remember { mutableStateOf<GLSurfaceView?>(null) }
    view?.let { glView ->
        LifecycleResumeEffect(glView) {
            glView.onResume()
            val pacer = FramePacer(glView).apply { start() }
            onPauseOrDispose {
                pacer.stop()
                glView.onPause()
            }
        }
        LaunchedEffect(glView, config) {
            glView.queueEvent { renderer.setConfig(config) }
        }
        PreviewGyroscope(glView, renderer, enabled = followsGyroscope, input = config.params.input)
    }

    key(dragMode) {
        AndroidView(
            factory = { viewContext ->
                GLSurfaceView(viewContext).apply {
                    setEGLContextClientVersion(GLES_VERSION)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    when (dragMode) {
                        DragMode.GYRO -> emulateGyroOnDrag(renderer)
                        DragMode.ORBIT -> orbitOnDrag(renderer)
                    }
                }.also { view = it }
            },
            onRelease = { released -> if (view === released) view = null },
            modifier = modifier,
        )
    }
}

/** Asks [view] for a frame on the vsyncs [FramePacing] picks at [PREVIEW_FPS]. Main thread. */
private class FramePacer(private val view: GLSurfaceView) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var lastFrameNanos = Long.MIN_VALUE
    private var running = false

    fun start() {
        running = true
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameNanos: Long) {
        if (!running) return
        val vsyncNanos = (1e9 / (view.display?.refreshRate?.takeIf { it > 0f } ?: 60f)).toLong()
        if (FramePacing.isDue(frameNanos, lastFrameNanos, PREVIEW_FPS, vsyncNanos)) {
            lastFrameNanos = frameNanos
            view.requestRender()
        }
        choreographer.postFrameCallback(this)
    }
}

/** Up raises the camera and sideways turns it, like tilting the phone; letting go recentres it. */
@SuppressLint("ClickableViewAccessibility") // A drag, not a click: there is nothing to perform.
private fun GLSurfaceView.emulateGyroOnDrag(renderer: PreviewGlRenderer) {
    val degreesPerPixel = GYRO_DEGREES_PER_100_DP / (100.0 * resources.displayMetrics.density)
    var downX = 0f
    var downY = 0f
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val pitch = (downY - event.y) * degreesPerPixel
                val yaw = (event.x - downX) * degreesPerPixel
                queueEvent { renderer.setGyroTarget(pitch, yaw, clamp = true) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> queueEvent { renderer.setGyroTarget(0.0, 0.0) }
        }
        true
    }
}

/** Sideways turns the camera around the hole, up and down changes its height; the view stays where it was left. */
@SuppressLint("ClickableViewAccessibility") // A drag, not a click: there is nothing to perform.
private fun GLSurfaceView.orbitOnDrag(renderer: PreviewGlRenderer) {
    val degreesPerPixel = ORBIT_DEGREES_PER_100_DP / (100.0 * resources.displayMetrics.density)
    var lastX = 0f
    var lastY = 0f
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The settings list scrolls around the preview: keep the drag here.
                view.parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val elevation = (event.y - lastY) * degreesPerPixel
                val azimuth = (lastX - event.x) * degreesPerPixel
                lastX = event.x
                lastY = event.y
                queueEvent { renderer.orbitBy(elevation, azimuth) }
            }
        }
        true
    }
}

private const val GLES_VERSION = 3
private const val PREVIEW_FPS = 60
private const val GYRO_DEGREES_PER_100_DP = 3.0
private const val ORBIT_DEGREES_PER_100_DP = 40.0
