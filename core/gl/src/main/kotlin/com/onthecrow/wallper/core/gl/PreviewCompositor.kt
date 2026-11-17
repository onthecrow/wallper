package com.onthecrow.wallper.core.gl

import timber.log.Timber

/** What a series' preview shows in one frame: one slide's renderer, or two blended [Blend.progress] of the way. */
sealed interface PreviewFrame {
    data class Single(val slide: GLWallpaperRenderer) : PreviewFrame

    data class Blend(val from: GLWallpaperRenderer, val to: GLWallpaperRenderer, val progress: Float) : PreviewFrame
}

/**
 * The renderer of a series' preview ([SeriesPreviewGl]). It draws nothing of its own: each frame is what
 * [frame] says, drawn with the slides' own renderers, which it sets up in its context ([add]: made and sized,
 * again after a lost context) and disposes of when they go ([remove]). A blend draws each slide into a target
 * of the [canvas] and mixes them at the progress it is given, not by a clock: the preview may stand still on
 * any moment. Of two slides only one with its picture is drawn alone; none leaves the window as it was.
 *
 * The state is reset whenever the context passes from one renderer to another ([resetState]), as on the
 * series' shared GL thread: each renderer was written to have a context to itself. A renderer that throws is
 * left out from then on, and a canvas that fails is given up on: the slides then cut. GL thread only, except
 * [frame].
 */
class PreviewCompositor internal constructor(
    private var canvas: TransitionCanvas?,
    private val resetState: (width: Int, height: Int) -> Unit,
    private var width: Int,
    private var height: Int,
) : GLWallpaperRenderer() {

    /** What the next frames draw; any thread, then ask the GL thread for a frame. */
    @Volatile
    var frame: PreviewFrame? = null

    /** The renderers set up, with the context they were set up in. */
    private val madeIn = LinkedHashMap<GLWallpaperRenderer, Int>()
    private val failed = HashSet<GLWallpaperRenderer>()
    private var context = 0
    private var lastUser: GLWallpaperRenderer? = null
    private var lastTarget: Int? = null
    private var pictured = false

    override val hasPicture: Boolean get() = pictured

    /** Sets [renderer] up now, before it is drawn: a video's texture gives its player a surface to decode into. */
    fun add(renderer: GLWallpaperRenderer) {
        if (renderer in failed) return
        guarded(renderer) { setUp(renderer) }
    }

    /** [renderer] is drawn no more; its GL objects go. */
    fun remove(renderer: GLWallpaperRenderer) {
        failed -= renderer
        if (madeIn.remove(renderer) == null) return
        try {
            enter(renderer, target = null)
            renderer.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "A preview slide's renderer failed to dispose")
        }
        lastUser = null
    }

    // Each renderer is set up when it is added.
    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        madeIn.filterValues { it == context }.keys.toList().forEach { renderer ->
            guarded(renderer) {
                enter(renderer, target = null)
                renderer.onSurfaceChanged(width, height)
            }
        }
    }

    override fun onDrawFrame(): Boolean {
        pictured = false
        // After a lost context: a video needs its surface back even while it isn't drawn.
        madeIn.filterValues { it != context }.keys.toList().forEach(::add)
        return when (val frame = frame) {
            null -> false
            is PreviewFrame.Single -> drawAlone(frame.slide)
            is PreviewFrame.Blend -> blend(frame.from, frame.to, frame.progress)
        }
    }

    override fun onContextLost() {
        context++
        lastUser = null
        lastTarget = null
        canvas?.onContextLost()
        madeIn.keys.forEach { renderer ->
            try {
                renderer.onContextLost()
            } catch (t: Throwable) {
                Timber.e(t, "A preview slide's renderer failed to forget its lost context")
            }
        }
    }

    override fun dispose() {
        frame = null
        madeIn.keys.toList().forEach(::remove)
        try {
            canvas?.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "The preview's transition failed to dispose")
        }
        canvas = null
    }

    private fun drawAlone(renderer: GLWallpaperRenderer): Boolean {
        val isNewFrame = draw(renderer, target = null)
        pictured = renderer.has()
        return isNewFrame
    }

    private fun blend(from: GLWallpaperRenderer, to: GLWallpaperRenderer, progress: Float): Boolean {
        val canvas = canvas ?: return drawAlone(to)
        return try {
            val isNewFrame = draw(from, TransitionCanvas.LEAVING) or draw(to, TransitionCanvas.COMING)
            when {
                from.has() && to.has() -> {
                    enterBlend()
                    try {
                        canvas.composite(progress, width, height)
                    } catch (t: Throwable) {
                        throw CanvasFailure(t)
                    }
                    pictured = true
                    isNewFrame
                }
                to.has() -> drawAlone(to)
                from.has() -> drawAlone(from)
                else -> isNewFrame
            }
        } catch (failure: CanvasFailure) {
            Timber.e(failure.cause, "The preview's transition failed: its slides cut from now on")
            dropCanvas()
            drawAlone(to)
        }
    }

    /** Draws [renderer] into [target] of the canvas, or the window; one that fails is left out. */
    private fun draw(renderer: GLWallpaperRenderer, target: Int?): Boolean {
        if (renderer in failed) return false
        return guarded(renderer) {
            if (madeIn[renderer] != context) setUp(renderer)
            enter(renderer, target)
            renderer.onDrawFrame()
        } ?: false
    }

    private fun setUp(renderer: GLWallpaperRenderer) {
        enter(renderer, target = null)
        renderer.onSurfaceCreated()
        renderer.onSurfaceChanged(width, height)
        madeIn[renderer] = context
        // Setting up may bind anything.
        lastUser = null
    }

    /** The context passes to [renderer], as a new one would come, drawing into [target] or the window. */
    private fun enter(renderer: GLWallpaperRenderer, target: Int?) {
        if (lastUser === renderer && lastTarget == target) return
        resetState(width, height)
        if (target != null) {
            val canvas = checkNotNull(canvas)
            try {
                canvas.bindTarget(target, width, height)
            } catch (t: Throwable) {
                throw CanvasFailure(t)
            }
        }
        lastUser = renderer
        lastTarget = target
    }

    private fun enterBlend() {
        resetState(width, height)
        lastUser = null
        lastTarget = null
    }

    private fun GLWallpaperRenderer.has(): Boolean = this !in failed && madeIn[this] == context && hasPicture

    /** Runs [block] for [renderer]; a failure of its own leaves it out, a canvas' goes on up. */
    private inline fun <T> guarded(renderer: GLWallpaperRenderer, block: () -> T): T? = try {
        block()
    } catch (failure: CanvasFailure) {
        throw failure
    } catch (t: Throwable) {
        Timber.e(t, "A preview slide's renderer failed: it is left out")
        failed += renderer
        lastUser = null
        null
    }

    private fun dropCanvas() {
        val dropped = canvas ?: return
        canvas = null
        try {
            dropped.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "The preview's failed transition failed to dispose")
        }
        lastUser = null
        lastTarget = null
    }

    private class CanvasFailure(cause: Throwable) : RuntimeException(cause)
}
