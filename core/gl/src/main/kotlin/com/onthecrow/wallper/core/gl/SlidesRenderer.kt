package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.SlideTransition
import timber.log.Timber

/**
 * The renderer of the one GL thread a series' slides share ([SharedGl]). It draws nothing of its own: the
 * slide on screen draws each frame with its own renderer, and a slide that warms up draws beside it,
 * unseen. It adds [resetState] whenever the context passes from one renderer to another: each was written to
 * have a context to itself.
 *
 * With a [transition] (and its [canvas]) a slide put on screen comes in through it: the slide it replaces
 * («leaving») goes on drawing, each of the two into a target of its own, and the frame is their blend. The
 * blend starts once the slide coming in has its picture; until then the one leaving is on screen as before.
 * It lasts the transition's duration from there, the frames asked for by [requestRender], and ends early when
 * the slide leaving is removed. A canvas that fails is given up on: from then on the slides cut.
 *
 * A slide's renderer makes its GL objects at its first draw here, and again after the context was lost.
 * A renderer that throws is given up on alone, the others go on. GL thread only.
 *
 * @param resetState puts the context back to how a new one starts, for a surface of the size given.
 * @param width the surface's size until the GL thread reports it, [height] too: a slide may warm up before
 * the first frame, which is when the thread does.
 */
internal class SlidesRenderer(
    private val resetState: (width: Int, height: Int) -> Unit,
    private var width: Int,
    private var height: Int,
    private val transition: SlideTransition = SlideTransition.NONE,
    private var canvas: TransitionCanvas? = null,
    private val requestRender: () -> Unit = {},
    private val nowNanos: () -> Long = System::nanoTime,
) : GLWallpaperRenderer() {

    /**
     * One slide's renderer and who hears of it, both on the GL thread.
     *
     * @param onFirstPicture once, when the renderer has drawn its picture, shown or not.
     * @param onFailed once, when the renderer threw and is drawn no more.
     */
    class Slide(
        val renderer: GLWallpaperRenderer,
        internal val onFirstPicture: () -> Unit,
        internal val onFailed: (Throwable) -> Unit,
    ) {
        /** The context its GL objects were made in. */
        internal var context = NO_CONTEXT
        internal var hadPicture = false

        /** Drawn no more: it failed or was removed. A request to draw it that comes late changes nothing. */
        internal var dropped = false

        /** Frames in a row that threw while it was the one on screen. */
        internal var failedFrames = 0
    }

    /** The slides that have made GL objects and haven't been disposed of. */
    private val madeUp = mutableListOf<Slide>()
    private var shown: Slide? = null

    /** The slide [shown] replaces, drawn under the transition until it is over; null without one. */
    private var leaving: Slide? = null

    /** When the blend started; null while the slide coming in has no picture yet. */
    private var blendStartNanos: Long? = null

    /** The renderer that used the context last, null after a reset of it: the state in it is that one's. */
    private var lastUser: Slide? = null

    /** The target the last user drew into, null for the window. */
    private var lastTarget: Int? = null

    /** Counts the contexts: a slide made in an older one has nothing left. */
    private var context = 0

    private var framePictured = false

    /**
     * The last frame had a picture: the slide on screen's, the blend, or the slide leaving while the one coming
     * in has none yet. Without it the window keeps what it shows.
     */
    override val hasPicture: Boolean get() = framePictured

    // Each slide's renderer is told when it first draws.
    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        madeUp.filter { it.context == context }.forEach { slide ->
            try {
                enter(slide, target = null)
                slide.renderer.onSurfaceChanged(width, height)
            } catch (t: Throwable) {
                fail(slide, t)
            }
        }
    }

    override fun onDrawFrame(): Boolean {
        framePictured = false
        val slide = shown ?: return false
        val from = leaving?.takeIf { !it.dropped && it.context == context }
        if (from == null || canvas == null) {
            if (leaving != null) endTransition()
            return drawShown(slide, target = null)
        }
        return try {
            drawTransition(from, slide)
        } catch (failure: CanvasFailure) {
            // The slides drew; the canvas did not. They cut from now on, and this frame shows the slide alone.
            Timber.e(failure.cause, "The series' %s transition failed: the slides cut from now on", transition.effect)
            dropCanvas()
            drawShown(slide, target = null)
        }
    }

    /**
     * Draws [slide] where it isn't seen: into the frame the slide on screen draws over before it is shown.
     * False if [slide] is the one on screen: its frames are the thread's to draw.
     */
    fun warm(slide: Slide): Boolean {
        if (slide === shown) return false
        if (slide.dropped) return true
        try {
            draw(slide, target = null)
        } catch (t: Throwable) {
            fail(slide, t)
        }
        return true
    }

    /**
     * From the next frame on, [slide] is the one on screen: through the transition if there is one and the
     * slide before it drew its picture in this context.
     */
    fun show(slide: Slide) {
        val previous = shown
        shown = slide.takeUnless { it.dropped }
        if (shown === previous) return
        endTransition()
        if (canvas != null && previous != null && shown != null && previous.hadPicture && previous.context == context) {
            leaving = previous
        }
    }

    /** [slide] is drawn no more; its GL objects go. */
    fun remove(slide: Slide) {
        slide.dropped = true
        if (shown === slide) shown = null
        if (leaving === slide) endTransition()
        dispose(slide)
    }

    override fun onContextLost() {
        context++
        lastUser = null
        lastTarget = null
        // Its targets went with the context; the slide leaving, made in it, has nothing left to draw.
        canvas?.onContextLost()
        leaving = null
        blendStartNanos = null
        madeUp.forEach { slide ->
            try {
                slide.renderer.onContextLost()
            } catch (t: Throwable) {
                Timber.e(t, "A slide's renderer failed to forget its lost context")
            }
        }
    }

    override fun dispose() {
        shown = null
        endTransition()
        madeUp.toList().forEach(::dispose)
        try {
            canvas?.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "The series' transition failed to dispose")
        }
    }

    /** The slide on screen, into the window or a [target]; null if it failed, and it isn't on screen any more. */
    private fun drawShown(slide: Slide, target: Int?): Boolean {
        try {
            val isNewFrame = draw(slide, target)
            slide.failedFrames = 0
            framePictured = slide.renderer.hasPicture
            return isNewFrame
        } catch (failure: CanvasFailure) {
            throw failure
        } catch (t: Throwable) {
            // Once, it may be the context: the thread's guard makes a new one, as for a wallpaper alone.
            // If it throws in the new one too, it is the slide.
            if (++slide.failedFrames < FRAMES_TO_FAIL) throw t
            fail(slide, t)
            return false
        }
    }

    /**
     * A frame of the transition from [from] to [to]. Before [to] has its picture, [from] goes on as the frame;
     * from the first frame [to] has it, the two are blended. The frames ask for the next until the end.
     */
    private fun drawTransition(from: Slide, to: Slide): Boolean {
        val start = blendStartNanos ?: run {
            // Into the window, where the slide leaving draws over it.
            val comingIsNew = drawShown(to, target = null)
            if (shown !== to) return false
            if (!to.renderer.hasPicture) {
                val leavingIsNew = drawLeaving(from, target = null)
                framePictured = from.renderer.hasPicture
                return comingIsNew || leavingIsNew
            }
            nowNanos().also { blendStartNanos = it }
        }
        val progress = ((nowNanos() - start) / (transition.durationMs * NANOS_PER_MS)).coerceIn(0.0, 1.0).toFloat()
        val leavingIsNew = drawLeaving(from, TransitionCanvas.LEAVING)
        if (leaving == null) return drawShown(to, target = null)
        val comingIsNew = drawShown(to, TransitionCanvas.COMING)
        if (shown !== to) return false

        resetState(width, height)
        lastUser = null
        lastTarget = null
        onCanvas { it.composite(progress, width, height) }
        framePictured = true
        if (progress < 1f) requestRender() else endTransition()
        return comingIsNew || leavingIsNew
    }

    /** The slide leaving, as the transition's [target]; a failure takes it out of the transition. */
    private fun drawLeaving(slide: Slide, target: Int?): Boolean = try {
        draw(slide, target)
    } catch (failure: CanvasFailure) {
        throw failure
    } catch (t: Throwable) {
        fail(slide, t)
        false
    }

    private fun draw(slide: Slide, target: Int?): Boolean {
        enter(slide, target)
        if (slide.context != context) {
            if (slide !in madeUp) madeUp += slide
            slide.renderer.onSurfaceCreated()
            slide.renderer.onSurfaceChanged(width, height)
            slide.context = context
            // Setting up may have bound another framebuffer.
            target?.let { slot -> onCanvas { it.bindTarget(slot, width, height) } }
        }
        val isNewFrame = slide.renderer.onDrawFrame()
        if (slide.renderer.hasPicture && !slide.hadPicture) {
            slide.hadPicture = true
            slide.onFirstPicture()
        }
        return isNewFrame
    }

    /**
     * The context passes to [slide]'s renderer, which finds it as it would a new one, drawing into [target]
     * (of the canvas) or the window.
     */
    private fun enter(slide: Slide, target: Int?) {
        if (lastUser === slide && lastTarget == target) return
        resetState(width, height)
        target?.let { slot -> onCanvas { it.bindTarget(slot, width, height) } }
        lastUser = slide
        lastTarget = target
    }

    /** No transition under way: the slide leaving isn't drawn any more, and the targets' memory goes. */
    private fun endTransition() {
        val wasUnderWay = leaving != null
        leaving = null
        blendStartNanos = null
        if (!wasUnderWay) return
        try {
            canvas?.releaseTargets()
        } catch (t: Throwable) {
            Timber.e(t, "The series' transition failed to let go of its targets")
            dropCanvas()
        }
    }

    private fun dropCanvas() {
        val dropped = canvas ?: return
        canvas = null
        leaving = null
        blendStartNanos = null
        try {
            dropped.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "The series' failed transition failed to dispose")
        }
        lastUser = null
        lastTarget = null
    }

    private fun fail(slide: Slide, error: Throwable) {
        if (slide.dropped) return
        slide.dropped = true
        if (shown === slide) shown = null
        if (leaving === slide) endTransition()
        dispose(slide)
        slide.onFailed(error)
    }

    private fun dispose(slide: Slide) {
        if (!madeUp.remove(slide)) return
        try {
            enter(slide, target = null)
            slide.renderer.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "A slide's renderer failed to dispose")
        }
        // Whatever it left behind is nobody's.
        lastUser = null
    }

    /** Runs [block] on the canvas, which is there: what it throws is the canvas' failure, not a slide's. */
    private inline fun <T> onCanvas(block: (TransitionCanvas) -> T): T = try {
        block(checkNotNull(canvas))
    } catch (t: Throwable) {
        throw CanvasFailure(t)
    }

    private class CanvasFailure(cause: Throwable) : RuntimeException(cause)

    private companion object {
        const val NO_CONTEXT = -1

        /** The second frame in a row that throws is after the guard's new context. */
        const val FRAMES_TO_FAIL = 2

        const val NANOS_PER_MS = 1_000_000.0
    }
}
