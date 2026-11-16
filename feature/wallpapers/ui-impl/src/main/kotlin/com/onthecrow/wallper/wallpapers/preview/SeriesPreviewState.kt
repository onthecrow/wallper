package com.onthecrow.wallper.wallpapers.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.core.engine.SeriesPreview
import com.onthecrow.wallper.core.engine.SeriesPreviews
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.SlideTransition

/** A series to preview: its [slides] in turn, each alone for [holdMs], coming in through [transition]. */
@Immutable
internal data class SeriesPreviewSpec(
    val slides: List<WallpaperSpec>,
    val transition: SlideTransition,
    val holdMs: Long,
)

/**
 * A series' preview on a [TextureView] ([SeriesPreviewSurface]), playing while the screen is resumed. A new
 * [spec] (another transition, say) or a new surface size makes the preview anew, from its start. Main thread only.
 */
internal class SeriesPreviewState(
    private val context: Context,
    private val previews: SeriesPreviews,
) : TextureView.SurfaceTextureListener {

    /** What it shows; null shows nothing yet. */
    var spec: SeriesPreviewSpec? = null
        set(value) {
            if (value == field) return
            field = value
            remake()
        }

    /** The screen is resumed: it plays. */
    var resumed = false
        set(value) {
            if (value == field) return
            field = value
            updatePlaying()
        }

    /** The preview on the surface, while there are one and a [spec]. */
    private var preview: SeriesPreview? = null
    private var holder: TextureSurfaceHolder? = null

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        holder = TextureSurfaceHolder(Surface(surface), width, height)
        remake()
    }

    // The videos are cropped for the size the preview was made at.
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        holder?.setSize(width, height)
        remake()
    }

    /** Returns once the preview has let go of the surface, which the view then releases. */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stop()
        holder?.surface?.release()
        holder = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    fun release() = stop()

    private fun remake() {
        stop()
        val holder = holder ?: return
        val spec = spec?.takeIf { it.slides.isNotEmpty() } ?: return
        preview = previews.create(context, holder, spec.slides, spec.transition, spec.holdMs)
        updatePlaying()
    }

    private fun stop() {
        preview?.release()
        preview = null
    }

    private fun updatePlaying() {
        val preview = preview ?: return
        if (resumed) preview.play() else preview.pause()
    }
}

/** The state of a preview of [spec], released when it leaves the composition. */
@Composable
internal fun rememberSeriesPreviewState(spec: SeriesPreviewSpec?): SeriesPreviewState {
    val previews = LocalSeriesPreviews.current
    val context = LocalContext.current.applicationContext
    val state = remember(previews, context) { SeriesPreviewState(context, previews) }
    SideEffect { state.spec = spec }
    LifecycleResumeEffect(state) {
        state.resumed = true
        onPauseOrDispose { state.resumed = false }
    }
    DisposableEffect(state) {
        onDispose { state.release() }
    }
    return state
}

/** Where [state]'s preview is drawn: black until its first frame. Nothing reaches it from the fingers. */
@Composable
internal fun SeriesPreviewSurface(state: SeriesPreviewState, modifier: Modifier = Modifier) {
    // Android Studio previews have no GL.
    if (LocalInspectionMode.current) {
        Box(modifier.background(Color.Black))
        return
    }
    AndroidView(
        factory = { viewContext -> TextureView(viewContext).apply { surfaceTextureListener = state } },
        modifier = modifier.background(Color.Black),
    )
}

/** Where the previews come from: core:engine:impl, provided by the screens' navigation entries. */
internal val LocalSeriesPreviews = staticCompositionLocalOf<SeriesPreviews> {
    error("No SeriesPreviews: provide LocalSeriesPreviews around the screen")
}

/**
 * A [TextureView]'s surface as a [SurfaceHolder]: the GL thread asks only for [getSurface] and
 * [getSurfaceFrame]. Canvases come straight from the surface; there are no callbacks.
 */
private class TextureSurfaceHolder(private val surface: Surface, width: Int, height: Int) : SurfaceHolder {

    @Volatile
    private var frame = Rect(0, 0, width, height)

    fun setSize(width: Int, height: Int) {
        frame = Rect(0, 0, width, height)
    }

    override fun getSurface(): Surface = surface

    override fun getSurfaceFrame(): Rect = Rect(frame)

    override fun isCreating(): Boolean = false

    override fun addCallback(callback: SurfaceHolder.Callback?) = Unit

    override fun removeCallback(callback: SurfaceHolder.Callback?) = Unit

    override fun setFixedSize(width: Int, height: Int) = Unit

    override fun setSizeFromLayout() = Unit

    override fun setFormat(format: Int) = Unit

    @Deprecated("Ignored by the system since API 11", ReplaceWith(""))
    override fun setType(type: Int) = Unit

    override fun setKeepScreenOn(screenOn: Boolean) = Unit

    override fun lockCanvas(): Canvas? = surface.lockCanvas(null)

    override fun lockCanvas(dirty: Rect?): Canvas? = surface.lockCanvas(dirty)

    override fun unlockCanvasAndPost(canvas: Canvas) = surface.unlockCanvasAndPost(canvas)
}
