package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class WarmStageTest {

    private val slides = listOf("a", "b", "c").map { WallpaperSpec.ProcessedVideo(it, thumbnailPath = "") }
    private val engines = mutableListOf<FakeSlide>()

    /** What the engines and the window were told, in order: "<slide path> <call>". */
    private val journal = mutableListOf<String>()

    /** What the series heard: the slide and why it gave up. */
    private val gaveUp = mutableListOf<Pair<Int, String>>()
    private var unmakeable: WallpaperSpec? = null

    private val window = object : SeriesWindow {
        override fun setVisible(visible: Boolean) {
            journal += "window visible=$visible"
        }
        override fun onSurfaceChanged(width: Int, height: Int) {
            journal += "window changed"
        }
        override fun onSurfaceDestroyed() {
            journal += "window destroyed"
        }
        override fun release() {
            journal += "window release"
        }
    }

    private fun TestScope.stage(
        handsOverWindow: Boolean = true,
        shared: Boolean = false,
        handover: Duration = Duration.ZERO,
    ) = WarmStage(
        slides = slides,
        hostOf = { slide ->
            object : EngineHost {
                override fun showPlaceholder(reason: String, error: Throwable?) {
                    gaveUp += slide to reason
                }
            }
        },
        scope = backgroundScope + UnconfinedTestDispatcher(testScheduler),
        handsOverWindow = handsOverWindow,
        window = window.takeIf { shared },
        handover = handover,
        createSlide = { slide, host, onReady ->
            if (slide == unmakeable) null else FakeSlide(slide, host, onReady, journal).also(engines::add)
        },
    )

    /** Starts preparing [slide] as the schedule does; the job is done once the stage stops waiting for it. */
    private fun TestScope.prepare(stage: WarmStage, slide: Int): Job =
        (backgroundScope + UnconfinedTestDispatcher(testScheduler)).launch { stage.prepare(slide) }

    @Test
    fun `the first slide is made, shown and resumed`() = runTest {
        stage().switchTo(0)

        assertEquals(listOf("a show", "a resume"), journal)
    }

    @Test
    fun `a prepared slide takes the window the old one let go of, and the old one goes after`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        journal.clear()

        val preparing = prepare(stage, 1)
        assertFalse(preparing.isCompleted)
        engines[1].ready()
        assertTrue(preparing.isCompleted)

        stage.switchTo(1)

        assertEquals(listOf("a pause", "a surfaceDestroyed", "b show", "b resume", "a release"), journal)
    }

    @Test
    fun `slides that share the window don't hand it over`() = runTest {
        val stage = stage(handsOverWindow = false)
        stage.switchTo(0)
        prepare(stage, 1)
        engines[1].ready()
        journal.clear()

        stage.switchTo(1)

        assertEquals(listOf("a pause", "b show", "b resume", "a release"), journal)
    }

    @Test
    fun `under a transition the old slide plays on for the handover, then goes`() = runTest {
        val stage = stage(handsOverWindow = false, shared = true, handover = 1_500.milliseconds)
        stage.switchTo(0)
        prepare(stage, 1)
        engines[1].ready()
        journal.clear()

        stage.switchTo(1)
        assertEquals(listOf("b show", "b resume"), journal)

        advanceTimeBy(1_499.milliseconds)
        assertFalse(engines[0].released)
        advanceTimeBy(2.milliseconds)
        assertEquals(listOf("b show", "b resume", "a pause", "a release"), journal)
    }

    @Test
    fun `the old slide under a transition follows the launcher's pages`() = runTest {
        val stage = stage(handsOverWindow = false, shared = true, handover = 1_500.milliseconds)
        stage.switchTo(0)
        prepare(stage, 1)
        engines[1].ready()
        stage.switchTo(1)

        stage.onOffsetsChanged(0.25f, 0.5f)

        assertEquals(0.25f to 0.5f, engines[0].offsets)
    }

    @Test
    fun `a slide that isn't ready yet, or a hidden wallpaper, cuts despite the transition`() = runTest {
        val stage = stage(handsOverWindow = false, shared = true, handover = 1_500.milliseconds)
        stage.switchTo(0)
        prepare(stage, 1)
        journal.clear()
        advanceTimeBy(WarmStage.PREPARE_TIMEOUT)

        stage.switchTo(1)
        assertEquals(listOf("a pause", "b show", "b resume", "a release"), journal)

        prepare(stage, 2)
        engines[2].ready()
        stage.pause()
        journal.clear()
        stage.switchTo(2)
        assertEquals(listOf("b pause", "c show", "c pause", "b release"), journal)
    }

    @Test
    fun `hiding the wallpaper, a new switch or a release let the old slide go at once`() = runTest {
        val stage = stage(handsOverWindow = false, shared = true, handover = 1_500.milliseconds)
        stage.switchTo(0)
        prepare(stage, 1)
        engines[1].ready()
        stage.switchTo(1)

        stage.pause()
        assertTrue(engines[0].released)

        stage.resume()
        prepare(stage, 2)
        engines[2].ready()
        stage.switchTo(2)
        stage.switchTo(0)
        assertTrue(engines[1].released)

        prepare(stage, 1)
        engines.last().ready()
        stage.switchTo(1)
        stage.release()
        assertTrue(engines.all { it.released })
    }

    @Test
    fun `with nothing prepared the old slide is released before the new one is made`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        journal.clear()

        stage.switchTo(1)

        assertEquals(listOf("a pause", "a surfaceDestroyed", "a release", "b show", "b resume"), journal)
        assertEquals(2, engines.size)
    }

    @Test
    fun `a slide shown while the wallpaper is hidden stays paused`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        stage.pause()
        journal.clear()

        stage.switchTo(1)

        assertEquals(listOf("a pause", "a surfaceDestroyed", "a release", "b show", "b pause"), journal)
    }

    @Test
    fun `a slide that isn't ready in time is kept and shown as it is`() = runTest {
        val stage = stage()
        stage.switchTo(0)

        val preparing = prepare(stage, 1)
        advanceTimeBy(WarmStage.PREPARE_TIMEOUT.inWholeMilliseconds + 1)
        assertTrue(preparing.isCompleted)
        assertFalse(engines[1].released)
        journal.clear()

        stage.switchTo(1)

        assertEquals(listOf("a pause", "a surfaceDestroyed", "b show", "b resume", "a release"), journal)
        assertEquals(2, engines.size)
    }

    @Test
    fun `a slide prepared for another turn is let go of`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        prepare(stage, 1)
        engines[1].ready()

        stage.switchTo(2)

        assertTrue(engines[1].released)
        assertEquals(slides[2], engines[2].slide)
    }

    @Test
    fun `a slide that gives up while prepared is let go of, and tried again alone at its turn`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        val preparing = prepare(stage, 1)

        engines[1].host.showPlaceholder("no decoder")

        assertTrue(preparing.isCompleted)
        assertTrue(engines[1].released)
        assertTrue(gaveUp.isEmpty())

        stage.switchTo(1)
        assertEquals(3, engines.size)
        assertEquals(slides[1], engines[2].slide)
    }

    @Test
    fun `a slide that gives up on screen is the series' to skip`() = runTest {
        val stage = stage()
        stage.switchTo(0)

        engines[0].host.showPlaceholder("broken")

        assertEquals(listOf(0 to "broken"), gaveUp)
    }

    @Test
    fun `a slide switched away from is heard no more`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        stage.switchTo(1)

        engines[0].host.showPlaceholder("late")

        assertTrue(gaveUp.isEmpty())
    }

    @Test
    fun `a slide that can't be made off screen gives up at its turn`() = runTest {
        unmakeable = slides[1]
        val stage = stage()
        stage.switchTo(0)

        val preparing = prepare(stage, 1)
        assertTrue(preparing.isCompleted)
        assertTrue(gaveUp.isEmpty())

        stage.switchTo(1)

        assertEquals(1, gaveUp.single().first)
        assertTrue(engines[0].released)
    }

    @Test
    fun `cancelling the preparation lets the slide go`() = runTest {
        val stage = stage()
        stage.switchTo(0)

        prepare(stage, 1).cancel()

        assertTrue(engines[1].released)
    }

    @Test
    fun `dropping the prepared slide releases it and keeps the one on screen`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        prepare(stage, 1)
        engines[1].ready()

        stage.dropPrepared()

        assertTrue(engines[1].released)
        assertFalse(engines[0].released)
    }

    @Test
    fun `a new slide has the launcher's page and zoom before it shows`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        stage.onOffsetsChanged(0.25f, 0.5f)
        stage.onZoomChanged(0.3f)
        journal.clear()

        prepare(stage, 1)

        assertEquals(0.25f to 0.5f, engines[1].offsets)
        assertEquals(0.3f, engines[1].zoom)
        assertEquals(emptyList(), journal)

        stage.onOffsetsChanged(0.75f, 0.5f)
        assertEquals(0.75f to 0.5f, engines[0].offsets)
        assertEquals(0.75f to 0.5f, engines[1].offsets)
    }

    @Test
    fun `a changed or lost surface lets the prepared slide go`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        prepare(stage, 1)

        stage.onSurfaceChanged(1080, 2400)
        assertTrue(engines[1].released)

        prepare(stage, 1)
        stage.onSurfaceDestroyed()
        assertTrue(engines[2].released)
        assertTrue(engines[0].surfaceDestroyed)
        assertFalse(engines[0].released)
    }

    @Test
    fun `the shared window hears what the stage hears, visible before the slide and released after it`() = runTest {
        val stage = stage(handsOverWindow = false, shared = true)
        stage.switchTo(0)
        journal.clear()

        stage.pause()
        stage.resume()
        stage.onSurfaceChanged(1080, 2400)
        stage.onSurfaceDestroyed()
        stage.release()

        assertEquals(
            listOf(
                "a pause", "window visible=false",
                "window visible=true", "a resume",
                "a surfaceChanged", "window changed",
                "a surfaceDestroyed", "window destroyed",
                "a release", "window release",
            ),
            journal,
        )
    }

    @Test
    fun `release lets go of both slides`() = runTest {
        val stage = stage()
        stage.switchTo(0)
        prepare(stage, 1)

        stage.release()

        assertTrue(engines.all { it.released })
        assertNull(engines.getOrNull(2))
    }

    private class FakeSlide(
        val slide: WallpaperSpec,
        val host: EngineHost,
        val ready: () -> Unit,
        private val journal: MutableList<String>,
    ) : WallpaperEngine() {
        var released = false
        var surfaceDestroyed = false
        var offsets: Pair<Float, Float>? = null
        var zoom: Float? = null

        private fun log(call: String) {
            journal += "${(slide as WallpaperSpec.ProcessedVideo).path} $call"
        }

        override fun show() = log("show")

        override fun pause() = log("pause")

        override fun resume() = log("resume")

        override fun release() {
            released = true
            log("release")
        }

        override fun onSurfaceChanged(width: Int, height: Int) = log("surfaceChanged")

        override fun onSurfaceDestroyed() {
            surfaceDestroyed = true
            log("surfaceDestroyed")
        }

        override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
            offsets = xOffset to xOffsetStep
        }

        override fun onZoomChanged(zoom: Float) {
            this.zoom = zoom
        }
    }
}
