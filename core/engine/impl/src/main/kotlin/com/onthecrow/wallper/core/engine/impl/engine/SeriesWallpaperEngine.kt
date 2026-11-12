package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.SlideDuration
import kotlinx.coroutines.CoroutineScope
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
 * A series: one of [slides] at a time, each through its own engine ([createSlide]), the next one after
 * [slideDuration] of the time the wallpaper is visible. With [SlideDuration.ON_WAKE] the next one comes at
 * each screen-off, so that it is there when the screen turns on: no switch on screen. Not at the broadcast
 * itself: it comes as the screen starts to go off, and the wallpaper shows through the animation that
 * follows. The switch waits until the service hides the wallpaper, or [HIDE_WAIT] at most for a device that
 * doesn't (one keeping it «visible» under the always-on display), and only while the screen stays off
 * ([isScreenOn]): a screen back on before keeps the slide for this wake.
 *
 * A switch releases the slide's engine, which lets go of the surface before it returns, and only then
 * creates the next one: a cut, like a wallpaper the user picks. A slide whose engine gives up is skipped
 * from then on; when every one has, [host] shows the placeholder.
 *
 * Called on the main thread like every engine; [scope] must run there too. [timeSource] measures the
 * visible time.
 */
internal class SeriesWallpaperEngine(
    private val slides: List<WallpaperSpec>,
    private val slideDuration: SlideDuration,
    screenOffs: Flow<Unit>,
    private val isScreenOn: () -> Boolean,
    scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val host: EngineHost,
    private val createSlide: (slide: WallpaperSpec, host: EngineHost) -> WallpaperEngine,
) : WallpaperEngine() {

    private val scope = scope + SupervisorJob(scope.coroutineContext[Job])

    /** The slide on screen and its engine. */
    private var index = 0
    private var engine: WallpaperEngine

    private val failed = mutableSetOf<Int>()
    private var visible = true
    private var hasSurface = true
    private var released = false

    // Replayed to each new slide's engine: the launcher doesn't send them again.
    private var offsets: Pair<Float, Float>? = null
    private var zoom: Float? = null

    /** The visible time the slide on screen had before [visibleSince], which is null while hidden. */
    private var shownBefore = Duration.ZERO
    private var visibleSince: TimeMark? = timeSource.markNow()
    private var timer: Job? = null

    /** The screen went off: the next slide waits for the wallpaper to hide. */
    private var wakeSwitchDue = false
    private var hideWait: Job? = null

    init {
        require(slides.isNotEmpty()) { "A series needs slides" }
        engine = createSlide(slides[index], SlideHost(index))
        if (slides.size > 1 && slideDuration.isOnWake) {
            this.scope.launch { screenOffs.collect { onScreenOff() } }
        }
        startTimer()
    }

    override fun pause() {
        engine.pause()
        if (!visible) return
        visible = false
        visibleSince?.let { shownBefore += it.elapsedNow() }
        visibleSince = null
        timer?.cancel()
        switchForWake()
    }

    override fun resume() {
        engine.resume()
        // Shown again before it was hidden with the screen off: this wake keeps its slide.
        switchForWake()
        if (visible) return
        visible = true
        visibleSince = timeSource.markNow()
        startTimer()
    }

    override fun onSurfaceChanged(width: Int, height: Int) = engine.onSurfaceChanged(width, height)

    /** No more switches: the service makes a new engine for the next surface. */
    override fun onSurfaceDestroyed() {
        hasSurface = false
        timer?.cancel()
        engine.onSurfaceDestroyed()
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        offsets = xOffset to xOffsetStep
        engine.onOffsetsChanged(xOffset, xOffsetStep)
    }

    override fun onZoomChanged(zoom: Float) {
        this.zoom = zoom
        engine.onZoomChanged(zoom)
    }

    override fun release() {
        released = true
        hideWait?.cancel()
        scope.cancel()
        engine.release()
    }

    /** Counts down what is left of the slide's time, while visible; a series switching on wakes has none. */
    private fun startTimer() {
        timer?.cancel()
        if (slides.size < 2 || slideDuration.isOnWake || !visible || !hasSurface || released) return
        val left = slideDuration.ms.milliseconds - shownBefore - (visibleSince?.elapsedNow() ?: Duration.ZERO)
        timer = scope.launch {
            delay(left)
            next()
        }
    }

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
        (1..slides.size).map { (after + it) % slides.size }.firstOrNull { it !in failed }

    private fun show(slide: Int) {
        engine.release()
        index = slide
        shownBefore = Duration.ZERO
        visibleSince = if (visible) timeSource.markNow() else null
        engine = createSlide(slides[slide], SlideHost(slide))
        if (!visible) engine.pause()
        offsets?.let { (xOffset, xOffsetStep) -> engine.onOffsetsChanged(xOffset, xOffsetStep) }
        zoom?.let(engine::onZoomChanged)
        startTimer()
    }

    private fun onSlideFailed(slide: Int, reason: String, error: Throwable?) {
        // A slide that was already switched away from may still report.
        if (released || slide != index || !failed.add(slide)) return
        Timber.e(error, "Slide %d of %d gave up: %s", slide, slides.size, reason)
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
