package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A change without a seam: the next slide is made off screen [LEAD] before its turn, beside the one that
 * shows, and takes the screen once its picture is drawn. A slide made off screen shows no frame before it
 * has its picture, so the window keeps the slide before it until then: late, the change is a held frame,
 * never a black one.
 *
 * Two slides live only from the start of [prepare] to the switch: the old one is released right after it.
 * A switch with nothing prepared (the first slide, a change while the wallpaper is hidden, a slide that
 * gave up) releases the old slide first, so memory and decoders never hold more than one slide then.
 *
 * @param scope runs on the main thread; slides report from their own threads.
 * @param handsOverWindow each slide draws on the window by itself, and only one can: the old one lets go of
 * it before the new one takes it. False when [window] is the one producer and the slides draw through it.
 * @param window the GL thread the slides share, if they do: the stage tells it what it would tell a slide.
 * @param createSlide makes a slide's engine off screen, hidden; null if the slide can't be made that way.
 * `onReady` is to be called once its picture is drawn, from any thread.
 */
internal class WarmStage(
    private val slides: List<WallpaperSpec>,
    private val hostOf: (slide: Int) -> EngineHost,
    private val scope: CoroutineScope,
    private val handsOverWindow: Boolean,
    private val window: SeriesWindow? = null,
    private val createSlide: (slide: WallpaperSpec, host: EngineHost, onReady: () -> Unit) -> WallpaperEngine?,
) : SlideStage() {

    /** The slide on screen, null before the first one and when it couldn't be made. */
    private var current: Slot? = null

    /** The slide prepared off screen for the next switch. */
    private var next: Slot? = null
    private var visible = true

    // Replayed to each new slide's engine: the launcher doesn't send them again.
    private var offsets: Pair<Float, Float>? = null
    private var zoom: Float? = null

    override val lead: Duration = LEAD

    override suspend fun prepare(slide: Int) {
        dropPrepared()
        val slot = create(slide) ?: return
        next = slot
        try {
            // Past the timeout the slide stays: it is shown as it is, and its picture comes when it comes.
            withTimeoutOrNull(PREPARE_TIMEOUT) { slot.ready.await() }
        } catch (e: CancellationException) {
            if (next === slot) dropPrepared()
            throw e
        }
    }

    override fun switchTo(slide: Int) {
        val old = current
        val prepared = next?.takeIf { it.slide == slide && !it.gaveUp }
        if (prepared == null) dropPrepared()
        next = null
        // No more frames are expected of it: letting go of the window must not look like a hang.
        old?.engine?.pause()
        if (handsOverWindow) old?.engine?.onSurfaceDestroyed()
        // Nothing prepared: the old slide's memory and decoder go before the new one's come.
        if (prepared == null) old?.engine?.release()

        val slot = prepared ?: create(slide)
        current = slot
        if (slot == null) return hostOf(slide).showPlaceholder("The slide can't be made off screen")
        slot.engine.show()
        if (visible) slot.engine.resume() else slot.engine.pause()
        if (prepared != null) old?.engine?.release()
    }

    override fun dropPrepared() {
        val slot = next ?: return
        next = null
        slot.engine.release()
        slot.ready.complete(false)
    }

    override fun pause() {
        visible = false
        current?.engine?.pause()
        window?.setVisible(false)
    }

    override fun resume() {
        visible = true
        window?.setVisible(true)
        current?.engine?.resume()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        dropPrepared()
        current?.engine?.onSurfaceChanged(width, height)
        window?.onSurfaceChanged(width, height)
    }

    override fun onSurfaceDestroyed() {
        dropPrepared()
        current?.engine?.onSurfaceDestroyed()
        window?.onSurfaceDestroyed()
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        offsets = xOffset to xOffsetStep
        current?.engine?.onOffsetsChanged(xOffset, xOffsetStep)
        next?.engine?.onOffsetsChanged(xOffset, xOffsetStep)
    }

    override fun onZoomChanged(zoom: Float) {
        this.zoom = zoom
        current?.engine?.onZoomChanged(zoom)
        next?.engine?.onZoomChanged(zoom)
    }

    override fun release() {
        dropPrepared()
        current?.engine?.release()
        current = null
        window?.release()
    }

    /** A slide's engine made off screen, with the launcher's page and zoom; null if it can't be made so. */
    private fun create(slide: Int): Slot? {
        val slot = Slot(slide)
        val engine = createSlide(slides[slide], slot) { slot.ready.complete(true) } ?: return null
        slot.engine = engine
        offsets?.let { (xOffset, xOffsetStep) -> engine.onOffsetsChanged(xOffset, xOffsetStep) }
        zoom?.let(engine::onZoomChanged)
        return slot
    }

    private fun onGaveUp(slot: Slot, reason: String, error: Throwable?) {
        when {
            // Off screen it isn't the slide's failure yet: it gets its turn alone, and gives up there if it must.
            slot === next -> {
                Timber.w(error, "Slide %d gave up while it was prepared: %s", slot.slide, reason)
                dropPrepared()
            }
            slot === current -> hostOf(slot.slide).showPlaceholder(reason, error)
            // Otherwise it was switched away from already.
        }
    }

    /** A slide and its engine; the engine's host too, so that the stage knows which slide speaks. */
    private inner class Slot(val slide: Int) : EngineHost {
        lateinit var engine: WallpaperEngine

        /** Completes with true once the picture is drawn, with false if the slide is let go of before. */
        val ready = CompletableDeferred<Boolean>()

        /** Set at once, from the engine's thread; the rest follows on the main thread. */
        @Volatile
        var gaveUp = false
            private set

        override fun showPlaceholder(reason: String, error: Throwable?) {
            gaveUp = true
            scope.launch { onGaveUp(this@Slot, reason, error) }
        }
    }

    companion object {
        /** Enough for a decoder to start and a picture to decode; two slides are alive this long. */
        val LEAD = 2.seconds

        /** How long a switch waits for a slide that isn't ready; with [LEAD], well under the 15 s a paused player keeps its decoder. */
        val PREPARE_TIMEOUT = 5.seconds
    }
}

/** The one producer on the wallpaper window when a series' slides share it: told what a slide's engine would be. */
internal interface SeriesWindow {
    fun setVisible(visible: Boolean)
    fun onSurfaceChanged(width: Int, height: Int)

    /** Returns once the window is let go of. */
    fun onSurfaceDestroyed()
    fun release()
}
