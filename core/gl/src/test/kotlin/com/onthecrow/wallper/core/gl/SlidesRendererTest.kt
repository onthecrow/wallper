package com.onthecrow.wallper.core.gl

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
