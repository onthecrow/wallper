package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.model.SlideDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A series: one of its [slideCount] slides at a time, the next one after [slideDuration] of the time the
 * wallpaper is visible. With [SlideDuration.ON_WAKE] the next one comes at each screen-off, so that it is
 * there when the screen turns on: no switch on screen. Not at the broadcast itself: it comes as the screen
 * starts to go off, and the wallpaper shows through the animation that follows. The switch waits until the
 * service hides the wallpaper, or [HIDE_WAIT] at most for a device that doesn't (one keeping it «visible»
 * under the always-on display), and only while the screen stays off ([isScreenOn]): a screen back on before
 * keeps the slide for this wake.
 *
 * This is only the schedule: which slide and when. How a slide gets onto the screen is its [SlideStage]'s
 * business (a cut, or a slide prepared ahead), and the stage is told [SlideStage.lead] before a slide's time
 * is up to prepare the next one. A slide whose engine gives up is skipped from then on; when every one has,
 * [host] shows the placeholder.
 *
 * Called on the main thread like every engine; [scope] must run there too. [timeSource] measures the
 * visible time.
 *
 * @param newStage makes the stage; a slide's engine reports to the host it gives for that slide.
 */
internal class SeriesWallpaperEngine(
    private val slideCount: Int,
    private val slideDuration: SlideDuration,
    screenOffs: Flow<Unit>,
    private val isScreenOn: () -> Boolean,
    scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val host: EngineHost,
    newStage: (hostOf: (slide: Int) -> EngineHost) -> SlideStage,
) : WallpaperEngine() {

    private val scope = scope + SupervisorJob(scope.coroutineContext[Job])

    private val stage = newStage(::SlideHost)

    /** The slide on screen. */
    private var index = 0

    private val failed = mutableSetOf<Int>()
    private var visible = true
    private var hasSurface = true
    private var released = false

    /** The visible time the slide on screen had before [visibleSince], which is null while hidden. */
    private var shownBefore = Duration.ZERO
    private var visibleSince: TimeMark? = timeSource.markNow()
    private var timer: Job? = null

    /** The screen went off: the next slide waits for the wallpaper to hide. */
    private var wakeSwitchDue = false
    private var hideWait: Job? = null

    init {
        require(slideCount > 0) { "A series needs slides" }
        stage.switchTo(index)
        if (slideCount > 1 && slideDuration.isOnWake) {
            this.scope.launch { screenOffs.collect { onScreenOff() } }
        }
        startTimer()
    }

    override fun pause() {
        stage.pause()
        if (!visible) return
        visible = false
        visibleSince?.let { shownBefore += it.elapsedNow() }
        visibleSince = null
        timer?.cancel()
        // A hidden wallpaper does no work: the next slide is prepared again once it shows.
        stage.dropPrepared()
        switchForWake()
    }

    override fun resume() {
        stage.resume()
        // Shown again before it was hidden with the screen off: this wake keeps its slide.
        switchForWake()
        if (visible) return
        visible = true
        visibleSince = timeSource.markNow()
        startTimer()
    }

    override fun onSurfaceChanged(width: Int, height: Int) = stage.onSurfaceChanged(width, height)

    /** No more switches: the service makes a new engine for the next surface. */
    override fun onSurfaceDestroyed() {
        hasSurface = false
        timer?.cancel()
        stage.onSurfaceDestroyed()
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) = stage.onOffsetsChanged(xOffset, xOffsetStep)

    override fun onZoomChanged(zoom: Float) = stage.onZoomChanged(zoom)

    override fun release() {
        released = true
        hideWait?.cancel()
        scope.cancel()
        stage.release()
    }

    /**
     * Counts down what is left of the slide's time, while visible; a series switching on wakes has none.
     * The stage prepares the next slide [SlideStage.lead] before the end, and the switch waits for it: a
     * slide that takes longer to get ready only keeps the one on screen a little longer.
     */
    private fun startTimer() {
        timer?.cancel()
        timer = null
        if (slideCount < 2 || slideDuration.isOnWake || !visible || !hasSurface || released) return
        // Known as the timer before it runs: with no time left it switches at once, in place, and the
        // timer that switch starts must not be taken for this one afterwards.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(timeLeft() - stage.lead)
            nextPlayable(after = index)?.takeIf { it != index }?.let { stage.prepare(it) }
            delay(timeLeft())
            next()
        }
        timer = job
        job.start()
    }

    /** What is left of the visible time of the slide on screen. */
    private fun timeLeft(): Duration =
        slideDuration.ms.milliseconds - shownBefore - (visibleSince?.elapsedNow() ?: Duration.ZERO)

    private fun onScreenOff() {
        wakeSwitchDue = true
        hideWait?.cancel()
        if (!visible) {
            switchForWake()
        } else {
            hideWait = scope.launch {
                delay(HIDE_WAIT)
                switchForWake()
            }
        }
    }

    /** The switch the screen-off made due, if the screen is still off; otherwise it is dropped. */
    private fun switchForWake() {
        if (!wakeSwitchDue) return
        wakeSwitchDue = false
        hideWait?.cancel()
        hideWait = null
        if (!isScreenOn()) next()
    }

    private fun next() {
        if (released || !hasSurface) return
        val next = nextPlayable(after = index) ?: return
        if (next != index) show(next)
    }

    /** The first slide after [after] that hasn't given up, round the end; [after] itself if it is the only one. */
    private fun nextPlayable(after: Int): Int? =
        (1..slideCount).map { (after + it) % slideCount }.firstOrNull { it !in failed }

    private fun show(slide: Int) {
        index = slide
        shownBefore = Duration.ZERO
        visibleSince = if (visible) timeSource.markNow() else null
        stage.switchTo(slide)
        startTimer()
    }

    private fun onSlideFailed(slide: Int, reason: String, error: Throwable?) {
        // A slide that was already switched away from may still report.
        if (released || slide != index || !failed.add(slide)) return
        Timber.e(error, "Slide %d of %d gave up: %s", slide, slideCount, reason)
        val next = nextPlayable(after = slide)
        if (next == null) {
            host.showPlaceholder("Every slide gave up, the last: $reason", error)
        } else {
            show(next)
        }
    }

    companion object {
        /** Longer than any screen-off animation: by then the screen is off or dozing, whatever the service says. */
        val HIDE_WAIT = 2_000.milliseconds
    }

    /** What a slide's engine may ask: giving up skips the slide. Callable from any thread. */
    private inner class SlideHost(private val slide: Int) : EngineHost {
        override fun showPlaceholder(reason: String, error: Throwable?) {
            scope.launch { onSlideFailed(slide, reason, error) }
        }
    }
}
