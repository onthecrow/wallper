package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import kotlin.time.Duration

/**
 * A cut, like a wallpaper the user picks: the slide's engine is released, which lets go of the surface
 * before it returns, and only then the next one is made. Nothing is prepared ahead.
 */
internal class CutStage(
    private val slides: List<WallpaperSpec>,
    private val hostOf: (slide: Int) -> EngineHost,
    private val createSlide: (slide: WallpaperSpec, host: EngineHost) -> WallpaperEngine,
) : SlideStage() {

    private var engine: WallpaperEngine? = null
    private var visible = true

    // Replayed to each new slide's engine: the launcher doesn't send them again.
    private var offsets: Pair<Float, Float>? = null
    private var zoom: Float? = null

    override val lead: Duration = Duration.ZERO

    override suspend fun prepare(slide: Int) = Unit

    override fun dropPrepared() = Unit

    override fun switchTo(slide: Int) {
        engine?.release()
        val next = createSlide(slides[slide], hostOf(slide))
        engine = next
        if (!visible) next.pause()
        offsets?.let { (xOffset, xOffsetStep) -> next.onOffsetsChanged(xOffset, xOffsetStep) }
        zoom?.let(next::onZoomChanged)
    }

    override fun pause() {
        visible = false
        engine?.pause()
    }

    override fun resume() {
        visible = true
        engine?.resume()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        engine?.onSurfaceChanged(width, height)
    }

    override fun onSurfaceDestroyed() {
        engine?.onSurfaceDestroyed()
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        offsets = xOffset to xOffsetStep
        engine?.onOffsetsChanged(xOffset, xOffsetStep)
    }

    override fun onZoomChanged(zoom: Float) {
        this.zoom = zoom
        engine?.onZoomChanged(zoom)
    }

    override fun release() {
        engine?.release()
        engine = null
    }
}
