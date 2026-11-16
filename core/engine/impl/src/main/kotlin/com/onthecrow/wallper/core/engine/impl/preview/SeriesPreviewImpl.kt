package com.onthecrow.wallper.core.engine.impl.preview

import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.SeriesPreview
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.engine.impl.BuiltInWallpapers
import com.onthecrow.wallper.core.engine.impl.engine.asWholeFrame
import com.onthecrow.wallper.core.gl.PreviewFrame
import com.onthecrow.wallper.core.gl.SeriesPreviewGl
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SeriesTimeline
import com.onthecrow.wallper.core.model.SeriesTimeline.Moment
import com.onthecrow.wallper.core.model.SlideTransition
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A [SeriesPreview] on [SeriesPreviewGl]: every frame is [SeriesTimeline.at] the position, which moves with
 * the clock while it plays and stands where it is put otherwise. So a pause, a seek and playing on look the
 * same as the moment they start from.
 *
 * Only the slides around the position live ([neighbours]: the ones on screen, the one before and the one
 * that comes next), so at most three players decode at once; the others are made as the position comes near
 * and let go of as it leaves. A slide that comes onto the screen, or any slide on a seek, is moved to its own
 * time there; one that plays on screen just plays, its player keeping time with the clock. The one coming
 * next waits at its start, still.
 *
 * Main thread only, like the players and the frame callbacks.
 */
internal class SeriesPreviewImpl(
    private val context: Context,
    holder: SurfaceHolder,
    private val slides: List<WallpaperSpec>,
    transition: SlideTransition,
    holdMs: Long,
    private val builtIns: BuiltInWallpapers,
) : SeriesPreview {

    override val timeline = SeriesTimeline(
        slideCount = slides.size,
        holdMs = holdMs,
        transitionMs = if (transition.isNone) 0 else transition.durationMs,
    )

    private val width = holder.surfaceFrame.width()
    private val height = holder.surfaceFrame.height()

    private val gl = SeriesPreviewGl(
        holder = holder,
        glesVersion = slides.maxOfOrNull(::glesVersionOf) ?: DEFAULT_GLES_VERSION,
        effect = transition.effect.takeUnless { transition.isNone || timeline.blendMs == 0L },
    )

    /** Pictures are decoded here, one at a time, away from the main thread and the GL thread. */
    private val decoder: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "PreviewDecoder") }

    private val alive = HashMap<Int, PreviewSlide>()

    /** The slides the last frame showed: one that wasn't among them comes onto the screen. */
    private var onScreen: Set<Int> = emptySet()

    private var scrubbing = false
    private var released = false

    override var positionMs = 0L
        private set

    override var isPlaying = false
        private set

    /** Where the clock started from while it plays: the position then and the uptime then. */
    private var clockFromMs = 0L
    private var clockStartedAtMs = 0L

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = Choreographer.FrameCallback { onVsync() }

    init {
        require(slides.isNotEmpty()) { "A preview needs slides" }
        showPosition(seek = true)
    }

    override fun play() {
        if (isPlaying || released) return
        isPlaying = true
        startClock()
        showPosition(seek = true)
        choreographer.postFrameCallback(frameCallback)
    }

    override fun pause() {
        if (!isPlaying) return
        positionMs = clockPosition()
        isPlaying = false
        choreographer.removeFrameCallback(frameCallback)
        // The players stop where they are: a seek would move them back or on by the moment it takes.
        showPosition(seek = false)
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        this.positionMs = Math.floorMod(positionMs, timeline.durationMs)
        if (isPlaying) startClock()
        showPosition(seek = true)
    }

    override fun setScrubbing(scrubbing: Boolean) {
        this.scrubbing = scrubbing
        alive.values.forEach { it.setScrubbing(scrubbing) }
    }

    override fun release() {
        if (released) return
        released = true
        isPlaying = false
        choreographer.removeFrameCallback(frameCallback)
        // The players first: they decode into the renderers' textures, which go with the GL thread.
        alive.values.forEach(PreviewSlide::release)
        alive.clear()
        gl.release(surfaceGoes = true)
        decoder.shutdownNow()
    }

    private fun onVsync() {
        if (!isPlaying || released) return
        positionMs = clockPosition()
        showPosition(seek = false)
        choreographer.postFrameCallback(frameCallback)
    }

    private fun startClock() {
        clockFromMs = positionMs
        clockStartedAtMs = SystemClock.uptimeMillis()
    }

    private fun clockPosition(): Long =
        Math.floorMod(clockFromMs + SystemClock.uptimeMillis() - clockStartedAtMs, timeline.durationMs)

    /**
     * Draws the moment at [positionMs]. With [seek] every slide on screen is moved to its own time there;
     * otherwise only one that comes onto the screen, or was just made.
     */
    private fun showPosition(seek: Boolean) {
        val moment = timeline.at(positionMs)
        val shown = moment.slides
        val wanted = shown.keys + neighbours(moment)
        val made = wanted.filter { it !in alive }.onEach { index -> alive[index] = make(index) }.toSet()
        wanted.forEach { index ->
            val slide = alive.getValue(index)
            val sinceShownMs = shown[index]
            if (sinceShownMs != null) {
                slide.moveTo(sinceShownMs, playing = isPlaying, seek = seek || index in made || index !in onScreen)
            } else {
                // It waits at the moment it comes in, still: a seek there finds it ready.
                slide.moveTo(sinceShownMs = 0, playing = false, seek = index in made)
            }
        }
        onScreen = shown.keys
        // The frame first: the slides let go of below must be out of it before the GL thread drops them.
        gl.show(frameOf(moment))
        (alive.keys - wanted).forEach(::drop)
    }

    /** Besides those on screen: the slide before (a finger may go back) and the one that comes next. */
    private fun neighbours(moment: Moment): Set<Int> {
        val count = slides.size
        return when (moment) {
            is Moment.Slide -> setOf(Math.floorMod(moment.index - 1, count), (moment.index + 1) % count)
            is Moment.Transition -> setOf((moment.to + 1) % count)
        }
    }

    private fun frameOf(moment: Moment): PreviewFrame? = when (moment) {
        is Moment.Slide -> alive[moment.index]?.renderer?.let { PreviewFrame.Single(it) }
        is Moment.Transition -> {
            val from = alive[moment.from]?.renderer
            val to = alive[moment.to]?.renderer
            when {
                from != null && to != null && from !== to -> PreviewFrame.Blend(from, to, moment.progress)
                to != null -> PreviewFrame.Single(to)
                from != null -> PreviewFrame.Single(from)
                else -> null
            }
        }
    }

    private fun make(index: Int): PreviewSlide {
        val slide = when (val wallpaper = slides[index]) {
            is WallpaperSpec.Media -> when (wallpaper.type) {
                MediaType.VIDEO -> VideoPreviewSlide(context, wallpaper, width, height, gl::requestRender)
                MediaType.IMAGE -> PicturePreviewSlide(wallpaper, width, height, decoder, gl::requestRender)
            }
            is WallpaperSpec.ProcessedVideo ->
                VideoPreviewSlide(context, wallpaper.asWholeFrame(), width, height, gl::requestRender)
            is WallpaperSpec.BuiltIn -> builtIns[wallpaper.wallpaper]
                ?.takeIf { it.isSupported() }
                ?.createScene(context, onChanged = gl::requestRender)
                ?.let { scene -> BuiltInPreviewSlide(scene, gl::requestRender) }
                ?: EmptyPreviewSlide
            // A series holds no series.
            is WallpaperSpec.Series -> EmptyPreviewSlide
        }
        slide.setScrubbing(scrubbing)
        slide.renderer?.let(gl::add)
        return slide
    }

    private fun drop(index: Int) {
        val slide = alive.remove(index) ?: return
        slide.release()
        slide.renderer?.let(gl::remove)
    }

    /** What a slide's renderer needs: a built-in may need more than the videos and pictures. */
    private fun glesVersionOf(slide: WallpaperSpec): Int = (slide as? WallpaperSpec.BuiltIn)
        ?.let { builtIns[it.wallpaper] }
        ?.takeIf { it.isSupported() }
        ?.glesVersion
        ?: DEFAULT_GLES_VERSION

    private companion object {
        const val DEFAULT_GLES_VERSION = 2
    }
}
