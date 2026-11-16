package com.onthecrow.wallper.core.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewCompositorTest {

    /** What the renderers, the canvas and the state reset were asked, in order. */
    private val journal = mutableListOf<String>()
    private val canvas = FakeCanvas()
    private val compositor = PreviewCompositor(canvas, { _, _ -> journal += "reset" }, width = 720, height = 1600)

    private val a = FakeRenderer("a")
    private val b = FakeRenderer("b")

    @Test
    fun `an added renderer is set up at once, before it is drawn`() {
        compositor.add(a)

        assertEquals(listOf("reset", "a created", "a changed 720x1600"), journal)
    }

    @Test
    fun `a single slide is drawn into the window with its picture`() {
        a.hasPicture = true
        compositor.add(a)
        journal.clear()
        compositor.frame = PreviewFrame.Single(a)

        compositor.onDrawFrame()

        // Setting up may have bound anything: the draw starts from a clean state.
        assertEquals(listOf("reset", "a draw"), journal)
        assertTrue(compositor.hasPicture)
    }

    @Test
    fun `a blend draws each slide into its target and mixes them at the progress it is given`() {
        a.hasPicture = true
        b.hasPicture = true
        compositor.add(a)
        compositor.add(b)
        journal.clear()
        compositor.frame = PreviewFrame.Blend(from = a, to = b, progress = 0.3f)

        compositor.onDrawFrame()
        compositor.onDrawFrame()

        val oneFrame = listOf("reset", "target 0", "a draw", "reset", "target 1", "b draw", "reset", "blend 0.3")
        assertEquals(oneFrame + oneFrame, journal)
        assertTrue(compositor.hasPicture)
    }

    @Test
    fun `of two slides the one with its picture is drawn alone`() {
        a.hasPicture = true
        compositor.frame = PreviewFrame.Blend(from = a, to = b, progress = 0.5f)

        compositor.onDrawFrame()

        assertEquals("a draw", journal.last())
        assertFalse("blend 0.5" in journal)
        assertTrue(compositor.hasPicture)
    }

    @Test
    fun `without any picture the frame is empty, and the surface keeps the last one`() {
        compositor.frame = PreviewFrame.Single(a)

        compositor.onDrawFrame()

        assertFalse(compositor.hasPicture)
        compositor.frame = null
        assertFalse(compositor.onDrawFrame())
    }

    @Test
    fun `a renderer that throws is left out, and the other slide goes on alone`() {
        a.throws = true
        b.hasPicture = true
        compositor.frame = PreviewFrame.Blend(from = a, to = b, progress = 0.5f)

        compositor.onDrawFrame()
        journal.clear()
        a.throws = false
        compositor.onDrawFrame()

        assertFalse("a draw" in journal)
        assertEquals("b draw", journal.last())
    }

    @Test
    fun `a canvas that fails is given up on, and the slide coming in is drawn alone from then on`() {
        a.hasPicture = true
        b.hasPicture = true
        canvas.failsToBlend = true
        compositor.frame = PreviewFrame.Blend(from = a, to = b, progress = 0.5f)

        compositor.onDrawFrame()
        journal.clear()
        compositor.onDrawFrame()

        assertTrue("canvas disposed" in canvas.calls)
        assertEquals("b draw", journal.last())
        assertFalse("target 0" in journal)
    }

    @Test
    fun `a lost context sets every renderer up again, drawn or not`() {
        compositor.add(a)
        compositor.add(b)
        compositor.frame = PreviewFrame.Single(a)
        compositor.onDrawFrame()

        compositor.onContextLost()
        journal.clear()
        compositor.onDrawFrame()

        assertTrue("canvas lost" in canvas.calls)
        assertTrue("a created" in journal)
        assertTrue("b created" in journal)
        assertEquals("a draw", journal.last())
    }

    @Test
    fun `a removed renderer is disposed of, and a new size reaches those still there`() {
        compositor.add(a)
        compositor.add(b)

        compositor.remove(a)
        compositor.onSurfaceChanged(1080, 2400)

        assertTrue("a disposed" in journal)
        assertTrue("b changed 1080x2400" in journal)
        assertFalse("a changed 1080x2400" in journal)
    }

    @Test
    fun `disposing lets go of every renderer and the canvas`() {
        compositor.add(a)
        compositor.add(b)

        compositor.dispose()

        assertTrue("a disposed" in journal)
        assertTrue("b disposed" in journal)
        assertTrue("canvas disposed" in canvas.calls)
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
            return false
        }

        override fun onContextLost() {
            journal += "$name contextLost"
        }

        override fun dispose() {
            journal += "$name disposed"
        }
    }
}
