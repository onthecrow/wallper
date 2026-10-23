package com.onthecrow.wallper.blackhole.gl

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * An ES 3.0 [GLSurfaceView] drawing [config] continuously. It pauses with the lifecycle, which drops
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
    val view = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(GLES_VERSION)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            when (dragMode) {
                DragMode.GYRO -> emulateGyroOnDrag(renderer)
                DragMode.ORBIT -> orbitOnDrag(renderer)
            }
        }
    }

    LifecycleResumeEffect(view) {
        view.onResume()
        onPauseOrDispose { view.onPause() }
    }
    LaunchedEffect(view, config) {
        view.queueEvent { renderer.setConfig(config) }
    }
    PreviewGyroscope(view, renderer, enabled = followsGyroscope, input = config.params.input)

    AndroidView(factory = { view }, modifier = modifier)
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
private const val GYRO_DEGREES_PER_100_DP = 3.0
private const val ORBIT_DEGREES_PER_100_DP = 40.0
