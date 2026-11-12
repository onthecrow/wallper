package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.SlideDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowWallpaperEngineTest {

    private val slides = listOf("a", "b", "c").map { WallpaperSpec.ProcessedVideo(it, thumbnailPath = "") }
    private val screenOffs = MutableSharedFlow<Unit>()
    private val engines = mutableListOf<FakeEngine>()
    private var placeholder: String? = null

    private val host = object : EngineHost {
        override fun showPlaceholder(reason: String, error: Throwable?) {
            placeholder = reason
        }
    }

    private fun TestScope.slideshow(duration: SlideDuration = SlideDuration(60_000)) = SlideshowWallpaperEngine(
        slides = slides,
        slideDuration = duration,
        screenOffs = screenOffs,
        scope = backgroundScope + UnconfinedTestDispatcher(testScheduler),
        timeSource = testScheduler.timeSource,
        host = host,
        createSlide = { slide, slideHost -> FakeEngine(slide, slideHost).also(engines::add) },
    )

    private val shown get() = engines.last()

    @Test
    fun `slides follow each other after their time, round the end`() = runTest {
        slideshow()
        assertEquals(slides[0], shown.slide)

        advanceTimeBy(60_001)
        assertEquals(slides[1], shown.slide)
        assertTrue(engines[0].released)

        advanceTimeBy(120_000)
        assertEquals(slides[0], shown.slide)
        assertEquals(4, engines.size)
    }

    @Test
    fun `only visible time counts`() = runTest {
        val slideshow = slideshow()

        advanceTimeBy(40_000)
        slideshow.pause()
        advanceTimeBy(600_000)
        slideshow.resume()
        advanceTimeBy(19_000)
        assertEquals(slides[0], shown.slide)

        advanceTimeBy(1_001)
        assertEquals(slides[1], shown.slide)
    }

    @Test
    fun `on wake the next slide comes at each screen-off, paused until it shows`() = runTest {
        val slideshow = slideshow(SlideDuration.ON_WAKE)
        advanceTimeBy(3_600_000)
        assertEquals(1, engines.size)

        slideshow.pause()
        screenOffs.emit(Unit)

        assertEquals(slides[1], shown.slide)
        assertTrue(shown.paused)
        slideshow.resume()
        assertFalse(shown.paused)
    }

    @Test
    fun `a new slide gets the launcher's page and zoom`() = runTest {
        val slideshow = slideshow()
        slideshow.onOffsetsChanged(0.25f, 0.5f)
        slideshow.onZoomChanged(0.3f)

        advanceTimeBy(60_001)

        assertEquals(0.25f to 0.5f, shown.offsets)
        assertEquals(0.3f, shown.zoom)
    }

    @Test
    fun `a slide that gives up is skipped from then on`() = runTest {
        slideshow()

        engines[0].host.showPlaceholder("broken")
        assertEquals(slides[1], shown.slide)
        assertNull(placeholder)

        advanceTimeBy(120_001)
        // Past c, round to b: a is out.
        assertEquals(slides[1], shown.slide)
    }

    @Test
    fun `when every slide gives up, the service shows the placeholder`() = runTest {
        slideshow()

        repeat(3) { shown.host.showPlaceholder("broken") }

        assertEquals(3, engines.size)
        assertTrue(placeholder.orEmpty().contains("broken"))
    }

    @Test
    fun `a slide switched away from can't skip the one on screen`() = runTest {
        slideshow()
        advanceTimeBy(60_001)

        engines[0].host.showPlaceholder("late")

        assertEquals(slides[1], shown.slide)
        assertEquals(2, engines.size)
    }

    @Test
    fun `released or without a surface, it switches no more`() = runTest {
        val slideshow = slideshow()
        slideshow.onSurfaceDestroyed()
        advanceTimeBy(600_000)
        assertEquals(1, engines.size)
        assertTrue(engines[0].surfaceDestroyed)

        slideshow.release()
        assertTrue(engines[0].released)
    }

    private class FakeEngine(val slide: WallpaperSpec, val host: EngineHost) : WallpaperEngine() {
        var paused = false
        var released = false
        var surfaceDestroyed = false
        var offsets: Pair<Float, Float>? = null
        var zoom: Float? = null

        override fun pause() {
            paused = true
        }

        override fun resume() {
            paused = false
        }

        override fun release() {
            released = true
        }

        override fun onSurfaceDestroyed() {
            surfaceDestroyed = true
        }

        override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
            offsets = xOffset to xOffsetStep
        }

        override fun onZoomChanged(zoom: Float) {
            this.zoom = zoom
        }
    }
}
