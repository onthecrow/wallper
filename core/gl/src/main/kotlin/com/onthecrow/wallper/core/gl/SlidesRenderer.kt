package com.onthecrow.wallper.core.gl

import timber.log.Timber

/**
 * The renderer of the one GL thread a series' slides share ([SharedGl]). It draws nothing of its own: the
 * slide on screen draws each frame with its own renderer, and a slide that warms up draws beside it,
 * unseen. All it adds is [resetState] whenever the context passes from one renderer to another: each was
 * written to have a context to itself.
 *
 * A slide's renderer makes its GL objects at its first draw here, and again after the context was lost.
 * A renderer that throws is given up on alone, the others go on. GL thread only.
 *
 * @param resetState puts the context back to how a new one starts, for a surface of the size given.
 */
internal class SlidesRenderer(
    private val resetState: (width: Int, height: Int) -> Unit,
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
        internal var failed = false

        /** Frames in a row that threw while it was the one on screen. */
        internal var failedFrames = 0
    }

    /** The slides that have made GL objects and haven't been disposed of. */
    private val madeUp = mutableListOf<Slide>()
    private var shown: Slide? = null

    /** The renderer that used the context last: the state in it is that one's. */
    private var lastUser: Slide? = null

    /** Counts the contexts: a slide made in an older one has nothing left. */
    private var context = 0
    private var width = 0
    private var height = 0

    /** The slide on screen has its picture: until then the frames are empty, and the window keeps what it shows. */
    override val hasPicture: Boolean
        get() = shown?.let { it.context == context && it.renderer.hasPicture } == true

    // Each slide's renderer is told when it first draws.
    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        madeUp.filter { it.context == context }.forEach { slide ->
            try {
                enter(slide)
                slide.renderer.onSurfaceChanged(width, height)
            } catch (t: Throwable) {
                fail(slide, t)
            }
        }
    }

    override fun onDrawFrame(): Boolean {
        val slide = shown ?: return false
        try {
            val isNewFrame = draw(slide)
            slide.failedFrames = 0
            return isNewFrame
        } catch (t: Throwable) {
            // Once, it may be the context: the thread's guard makes a new one, as for a wallpaper alone.
            // If it throws in the new one too, it is the slide.
            if (++slide.failedFrames < FRAMES_TO_FAIL) throw t
            fail(slide, t)
            return false
        }
    }

    /**
     * Draws [slide] where it isn't seen: into the frame the slide on screen draws over before it is shown.
     * False if [slide] is the one on screen: its frames are the thread's to draw.
     */
    fun warm(slide: Slide): Boolean {
        if (slide === shown) return false
        if (slide.failed) return true
        try {
            draw(slide)
        } catch (t: Throwable) {
            fail(slide, t)
        }
        return true
    }

    /** From the next frame on, [slide] is the one on screen. */
    fun show(slide: Slide) {
        shown = slide.takeUnless { it.failed }
    }

    /** [slide] is drawn no more; its GL objects go. */
    fun remove(slide: Slide) {
        if (shown === slide) shown = null
        dispose(slide)
    }

    override fun onContextLost() {
        context++
        lastUser = null
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
        madeUp.toList().forEach(::dispose)
    }

    private fun draw(slide: Slide): Boolean {
        enter(slide)
        if (slide.context != context) {
            if (slide !in madeUp) madeUp += slide
            slide.renderer.onSurfaceCreated()
            slide.renderer.onSurfaceChanged(width, height)
            slide.context = context
        }
        val isNewFrame = slide.renderer.onDrawFrame()
        if (slide.renderer.hasPicture && !slide.hadPicture) {
            slide.hadPicture = true
            slide.onFirstPicture()
        }
        return isNewFrame
    }

    /** The context passes to [slide]'s renderer, which finds it as it would a new one. */
    private fun enter(slide: Slide) {
        if (lastUser === slide) return
        resetState(width, height)
        lastUser = slide
    }

    private fun fail(slide: Slide, error: Throwable) {
        if (slide.failed) return
        slide.failed = true
        if (shown === slide) shown = null
        dispose(slide)
        slide.onFailed(error)
    }

    private fun dispose(slide: Slide) {
        if (!madeUp.remove(slide)) return
        try {
            enter(slide)
            slide.renderer.dispose()
        } catch (t: Throwable) {
            Timber.e(t, "A slide's renderer failed to dispose")
        }
        // Whatever it left behind is nobody's.
        lastUser = null
    }

    private companion object {
        const val NO_CONTEXT = -1

        /** The second frame in a row that throws is after the guard's new context. */
        const val FRAMES_TO_FAIL = 2
    }
}
