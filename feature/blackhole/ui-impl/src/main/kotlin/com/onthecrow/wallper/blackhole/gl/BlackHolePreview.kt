package com.onthecrow.wallper.blackhole.gl

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.blackhole.renderer.BlackHoleRendererFactory

/**
 * An ES 3.0 [GLSurfaceView] drawing [config] continuously, at the display's rate: unlike the wallpaper,
 * which stops at 60. It pauses with the lifecycle, which drops the EGL context; [PreviewGlRenderer] rebuilds its GPU objects on the next one. A drag on the picture
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
    // Android Studio previews have no GL and no renderer factory: the controls around still show.
    if (LocalInspectionMode.current) {
        Box(modifier = modifier)
        return
    }
    val renderers = LocalBlackHoleRenderers.current
    val currentOnStats by rememberUpdatedState(onStats)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val renderer = remember {
        PreviewGlRenderer(
            renderer = renderers.create(),
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
            onPauseOrDispose { glView.onPause() }
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
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
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

/** Where the preview's renderer comes from: logic-impl, provided by the screens' navigation entries. */
internal val LocalBlackHoleRenderers = staticCompositionLocalOf<BlackHoleRendererFactory> {
    error("No BlackHoleRendererFactory: provide LocalBlackHoleRenderers around the screen")
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
