package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.SlideTransition
import com.onthecrow.wallper.core.model.TransitionEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlidesRendererTest {

    /** What the slides' renderers and the state reset were asked, in order. */
    private val journal = mutableListOf<String>()
    private val slides = SlidesRenderer({ _, _ -> journal += "reset" }, width = 1080, height = 2400)

    private val a = FakeRenderer("a")
    private val b = FakeRenderer("b")
    private val slideA = slideOf(a)
    private val slideB = slideOf(b)

    private fun slideOf(renderer: FakeRenderer) = SlidesRenderer.Slide(
        renderer = renderer,
        onFirstPicture = { journal += "${renderer.name} firstPicture" },
        onFailed = { journal += "${renderer.name} failed" },
    )

    @Test
    fun `only the slide on screen draws the frame`() {
        slides.show(slideA)
        slides.onDrawFrame()
        slides.warm(slideB)
        journal.clear()

        slides.onDrawFrame()

        assertEquals(listOf("reset", "a draw"), journal)
    }

    @Test
    fun `nothing on screen is an empty frame`() {
        assertFalse(slides.onDrawFrame())
        assertFalse(slides.hasPicture)
    }

    @Test
    fun `a slide is set up at its first draw, once`() {
        slides.show(slideA)

        slides.onDrawFrame()
        slides.onDrawFrame()

        assertEquals(listOf("reset", "a created", "a changed 1080x2400", "a draw", "a draw"), journal)
    }

    @Test
    fun `the state is reset only when the context passes to another renderer`() {
        slides.show(slideA)
        slides.onDrawFrame()
        slides.onDrawFrame()
        slides.warm(slideB)
        slides.warm(slideB)
        slides.onDrawFrame()

        assertEquals(3, journal.count { it == "reset" })
    }

    @Test
    fun `the first picture is told once, shown or warming`() {
        slides.warm(slideB)
        assertFalse("b firstPicture" in journal)

        b.hasPicture = true
        slides.warm(slideB)
        slides.warm(slideB)
        slides.show(slideB)
        slides.onDrawFrame()

        assertEquals(1, journal.count { it == "b firstPicture" })
    }

    @Test
    fun `the frame has its picture when the slide on screen has`() {
        b.hasPicture = true
        slides.show(slideA)
        slides.warm(slideB)
        slides.onDrawFrame()
        assertFalse(slides.hasPicture)

        slides.show(slideB)
        slides.onDrawFrame()
        assertTrue(slides.hasPicture)
    }

    @Test
    fun `a new frame is the slide on screen's`() {
        a.newFrame = true
        slides.show(slideA)

        assertTrue(slides.onDrawFrame())
    }

    @Test
    fun `the slide on screen isn't warmed, its frames are the thread's`() {
        slides.show(slideA)

        assertFalse(slides.warm(slideA))
        assertTrue(slides.warm(slideB))
    }

    @Test
    fun `a slide that throws while it warms up fails alone`() {
        slides.show(slideA)
        b.throws = true

        slides.warm(slideB)
        slides.warm(slideB)

        assertEquals(1, journal.count { it == "b failed" })
        assertTrue("b disposed" in journal)
        journal.clear()
        slides.onDrawFrame()
        assertTrue("a draw" in journal)
    }

    @Test
    fun `the slide on screen may throw once, for the guard to make a new context`() {
        slides.show(slideA)
        a.throws = true

        assertFailsWith<IllegalStateException> { slides.onDrawFrame() }
        assertFalse("a failed" in journal)

        a.throws = false
        slides.onDrawFrame()
        a.throws = true
        assertFailsWith<IllegalStateException> { slides.onDrawFrame() }
        assertFalse("a failed" in journal)
    }

    @Test
    fun `a slide on screen that throws twice in a row fails, and the frame stays empty`() {
        slides.show(slideA)
        a.throws = true
        assertFailsWith<IllegalStateException> { slides.onDrawFrame() }

        assertFalse(slides.onDrawFrame())

        assertTrue("a failed" in journal)
        assertFalse(slides.hasPicture)
        journal.clear()
        slides.onDrawFrame()
        assertEquals(emptyList(), journal)
    }

    @Test
    fun `a lost context has every slide forget it and set itself up again`() {
        slides.show(slideA)
        slides.onDrawFrame()
        journal.clear()

        slides.onContextLost()
        slides.onSurfaceCreated()
        slides.onDrawFrame()

        assertEquals(listOf("a contextLost", "reset", "a created", "a changed 1080x2400", "a draw"), journal)
    }

    @Test
    fun `a new size reaches the slides that are set up`() {
        slides.show(slideA)
        slides.onDrawFrame()
        journal.clear()

        slides.onSurfaceChanged(2400, 1080)
        slides.warm(slideB)

        assertEquals(listOf("a changed 2400x1080", "reset", "b created", "b changed 2400x1080", "b draw"), journal)
    }

    @Test
    fun `a removed slide is disposed of and drawn no more`() {
        slides.show(slideA)
        slides.onDrawFrame()

        slides.remove(slideA)

        assertTrue("a disposed" in journal)
        journal.clear()
        assertFalse(slides.onDrawFrame())
        assertEquals(emptyList(), journal)
    }

    @Test
    fun `a request to draw a removed slide that comes late draws nothing`() {
        slides.warm(slideB)
        slides.remove(slideB)
        journal.clear()

        slides.warm(slideB)
        slides.show(slideB)
        slides.onDrawFrame()

        assertEquals(emptyList(), journal)
    }

    @Test
    fun `a slide that never drew has nothing to dispose of`() {
        slides.remove(slideB)

        assertEquals(emptyList(), journal)
    }

    @Test
    fun `disposing lets go of every slide that is set up`() {
        slides.show(slideA)
        slides.onDrawFrame()
        slides.warm(slideB)

        slides.dispose()

        assertTrue("a disposed" in journal)
        assertTrue("b disposed" in journal)
    }

    // Transitions: a renderer with a canvas, a clock and a count of the frames it asks for.

    private var nowMs = 0L
    private var renderRequests = 0
    private val canvas = FakeCanvas()
    private val blending = SlidesRenderer(
        resetState = { _, _ -> journal += "reset" },
        width = 1080,
        height = 2400,
        transition = SlideTransition(TransitionEffect.CROSSFADE, durationMs = 1_000),
        canvas = canvas,
        requestRender = { renderRequests++ },
        nowNanos = { nowMs * 1_000_000 },
    )

    /** A on screen with its picture, then B shown over it. */
    private fun blendFromAToB(bHasPicture: Boolean = true) {
        a.hasPicture = true
        b.hasPicture = bHasPicture
        blending.show(slideA)
        blending.onDrawFrame()
        blending.warm(slideB)
        blending.show(slideB)
        journal.clear()
    }

    @Test
    fun `a slide shown over another blends with it, each drawn into a target of its own`() {
        blendFromAToB()

        blending.onDrawFrame()

        assertEquals(
            listOf(
                // It warmed last: the context is still its own.
                "b draw",
                "reset", "target 0", "a draw", "reset", "target 1", "b draw", "reset", "blend 0.0",
            ),
            journal,
        )
        assertTrue(blending.hasPicture)
        assertEquals(1, renderRequests)
    }

    @Test
    fun `the blend goes by the clock from its first frame, and then the slide is alone`() {
        blendFromAToB()
        nowMs = 5_000
        blending.onDrawFrame()

        nowMs += 500
        journal.clear()
        blending.onDrawFrame()
        assertEquals("blend 0.5", journal.last())

        nowMs += 600
        journal.clear()
        blending.onDrawFrame()
        assertEquals(listOf("blend 1.0", "targets released"), journal.takeLast(2))
        assertEquals(2, renderRequests)

        journal.clear()
        blending.onDrawFrame()
        assertEquals(listOf("reset", "b draw"), journal)
    }

    @Test
    fun `until the slide coming in has its picture the one leaving stays on screen and goes on`() {
        blendFromAToB(bHasPicture = false)

        blending.onDrawFrame()

        assertEquals(listOf("b draw", "reset", "a draw"), journal)
        assertTrue(blending.hasPicture)

        b.hasPicture = true
        nowMs = 2_000
        journal.clear()
        blending.onDrawFrame()
        assertEquals("blend 0.0", journal.last())
    }

    @Test
    fun `removing the slide leaving ends the transition`() {
        blendFromAToB()
        blending.onDrawFrame()

        blending.remove(slideA)
        journal.clear()
        blending.onDrawFrame()

        assertTrue("targets released" in canvas.calls)
        assertEquals(listOf("reset", "b draw"), journal)
    }

    @Test
    fun `a slide that never had its picture is cut away from`() {
        b.hasPicture = true
        blending.show(slideA)
        blending.onDrawFrame()
        blending.warm(slideB)
        blending.show(slideB)
        journal.clear()

        blending.onDrawFrame()

        assertEquals(listOf("b draw"), journal)
    }

    @Test
    fun `a canvas that fails is given up on, and the slides cut from then on`() {
        blendFromAToB()
        canvas.failsToBlend = true

        blending.onDrawFrame()

        assertEquals(listOf("reset", "b draw"), journal.takeLast(2))
        assertTrue("canvas disposed" in canvas.calls)
        assertFalse("a failed" in journal || "b failed" in journal)

        blending.show(slideA)
        journal.clear()
        blending.onDrawFrame()
        assertEquals(listOf("reset", "a draw"), journal)
    }

    @Test
    fun `the slide leaving that throws fails alone and the one coming in is drawn`() {
        blendFromAToB()
        a.throws = true

        blending.onDrawFrame()

        assertTrue("a failed" in journal)
        assertEquals(listOf("reset", "b draw"), journal.takeLast(2))
    }

    @Test
    fun `a lost context drops the transition`() {
        blendFromAToB()
        blending.onDrawFrame()

        blending.onContextLost()
        journal.clear()
        blending.onDrawFrame()

        assertTrue("canvas lost" in canvas.calls)
        assertEquals(listOf("reset", "b created", "b changed 1080x2400", "b draw"), journal)
    }

    private inner class FakeCanvas : TransitionCanvas {
        val calls = mutableListOf<String>()
        var failsToBlend = false

        override fun bindTarget(slot: Int, width: Int, height: Int) {
            journal += "target $slot"
        }

        override fun composite(progress: Float, width: Int, height: Int) {
            check(!failsToBlend) { "The blend failed" }
            journal += "blend $progress"
        }

        override fun releaseTargets() {
            journal += "targets released"
            calls += "targets released"
        }

        override fun onContextLost() {
            calls += "canvas lost"
        }

        override fun dispose() {
            calls += "canvas disposed"
        }
    }

    private inner class FakeRenderer(val name: String) : GLWallpaperRenderer() {
        override var hasPicture = false
        var newFrame = false
        var throws = false

        override fun onSurfaceCreated() {
            journal += "$name created"
        }

        override fun onSurfaceChanged(width: Int, height: Int) {
            journal += "$name changed ${width}x$height"
        }

        override fun onDrawFrame(): Boolean {
            check(!throws) { "$name can't draw" }
            journal += "$name draw"
            return newFrame
        }

        override fun onContextLost() {
            journal += "$name contextLost"
        }

        override fun dispose() {
            journal += "$name disposed"
        }
    }
}
