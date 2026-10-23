package com.onthecrow.wallper.core.blackhole.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FramePacingTest {

    private val frames = FrameParams()

    @Test
    fun `target follows activity, power save and heat`() {
        val medium = QualityLevel.MEDIUM
        assertEquals(60, FramePacing.targetFps(true, false, ThermalLevel.NORMAL, frames, medium))
        assertEquals(30, FramePacing.targetFps(false, false, ThermalLevel.NORMAL, frames, medium))
        assertEquals(15, FramePacing.targetFps(true, true, ThermalLevel.NORMAL, frames, medium))
        assertEquals(24, FramePacing.targetFps(false, false, ThermalLevel.MODERATE, frames, medium))
        assertEquals(FramePacing.STILL, FramePacing.targetFps(true, false, ThermalLevel.SEVERE, frames, medium))
        assertEquals(30, FramePacing.targetFps(true, false, ThermalLevel.NORMAL, frames, QualityLevel.LOW))
    }

    @Test
    fun `a 120 Hz display draws every second vsync at 60 fps`() {
        val vsync = 8_333_333L
        var last = Long.MIN_VALUE
        var drawn = 0
        for (i in 0 until 120) {
            val t = i * vsync
            if (FramePacing.isDue(t, last, 60, vsync)) { drawn++; last = t }
        }

        assertEquals(60, drawn)
    }

    @Test
    fun `a 60 Hz display draws every second vsync at 30 fps`() {
        val vsync = 16_666_667L
        assertTrue(FramePacing.isDue(2 * vsync, 0, 30, vsync))
        assertFalse(FramePacing.isDue(vsync, 0, 30, vsync))
    }

    @Test
    fun `a GPU that keeps up stays on High, however long its frames take at a low rate`() {
        val controller = QualityController(sampleCount = 10, warmupCount = 0)

        // 25 ms at 30 fps: a clocked-down GPU looks just like this, the old 2.5 ms rule dropped it to Low.
        repeat(10) { assertNull(controller.onGpuTime(25.0, 30)) }
        repeat(20) { assertNull(controller.onGpuTime(50.0, 30)) } // settled: no second measurement

        assertEquals(QualityLevel.HIGH, controller.level)
    }

    @Test
    fun `an overloaded GPU steps down until it keeps up`() {
        val controller = QualityController(sampleCount = 4, warmupCount = 0)

        repeat(3) { controller.onGpuTime(40.0, 30) }
        assertEquals(QualityLevel.MEDIUM, controller.onGpuTime(40.0, 30))
        repeat(3) { controller.onGpuTime(20.0, 60) } // 1.2 frames' worth at 60 fps
        assertEquals(QualityLevel.LOW, controller.onGpuTime(20.0, 60))
        repeat(10) { assertNull(controller.onGpuTime(40.0, 30)) } // nothing below Low

        assertEquals(QualityLevel.LOW, controller.level)
    }

    @Test
    fun `the median decides, so a few slow frames don't`() {
        val controller = QualityController(sampleCount = 10, warmupCount = 0)

        repeat(4) { controller.onGpuTime(60.0, 30) }
        repeat(6) { controller.onGpuTime(10.0, 30) }

        assertEquals(QualityLevel.HIGH, controller.level)
    }

    @Test
    fun `warm-up and still frames don't count`() {
        val controller = QualityController(sampleCount = 4, warmupCount = 5)

        repeat(5) { assertNull(controller.onGpuTime(100.0, 30)) } // the unlock animation
        repeat(10) { assertNull(controller.onGpuTime(100.0, FramePacing.STILL)) }
        repeat(3) { controller.onGpuTime(10.0, 30) }

        assertNull(controller.onGpuTime(10.0, 30))
        assertEquals(QualityLevel.HIGH, controller.level)
    }

    @Test
    fun `without a GPU timer Auto stays on Medium`() {
        assertEquals(QualityLevel.MEDIUM, QualityController().onNoGpuTimer())
        assertNull(QualityController(QualityLevel.LOW).onNoGpuTimer())
    }

    @Test
    fun `heat lowers the quality only while it lasts`() {
        val controller = QualityController(initial = QualityLevel.HIGH)

        assertEquals(QualityLevel.MEDIUM, controller.onThermal(ThermalLevel.MODERATE))
        assertNull(controller.onThermal(ThermalLevel.SEVERE))
        assertEquals(QualityLevel.HIGH, controller.onThermal(ThermalLevel.NORMAL))
        // Another hot spell goes no lower than the first one.
        assertEquals(QualityLevel.MEDIUM, controller.onThermal(ThermalLevel.MODERATE))
        assertEquals(QualityLevel.HIGH, controller.onThermal(ThermalLevel.NORMAL))
    }

    @Test
    fun `a hot GPU isn't measured and the measurement starts over once it cools`() {
        val controller = QualityController(sampleCount = 4, warmupCount = 2)

        repeat(5) { controller.onGpuTime(10.0, 30) }
        controller.onThermal(ThermalLevel.MODERATE)
        repeat(10) { assertNull(controller.onGpuTime(10.0, 30)) }
        controller.onThermal(ThermalLevel.NORMAL)

        repeat(5) { assertNull(controller.onGpuTime(50.0, 30)) }
        assertEquals(QualityLevel.MEDIUM, controller.onGpuTime(50.0, 30))
    }

    @Test
    fun `a measurement comes from one visible stretch`() {
        val controller = QualityController(sampleCount = 4, warmupCount = 2)

        repeat(5) { controller.onGpuTime(10.0, 30) }
        controller.onHidden()

        repeat(5) { assertNull(controller.onGpuTime(50.0, 30)) }
        assertEquals(QualityLevel.MEDIUM, controller.onGpuTime(50.0, 30))
    }
}

class StarMathTest {

    private val layer = StarLayer(cells = 256, density = 0.3, flux = 0.15)

    @Test
    fun `a few more stars only raise the density`() {
        assertEquals(StarLayer(256, 0.6, 0.15), StarMath.effectiveLayer(layer, 2.0))
    }

    @Test
    fun `many more stars get a finer grid with the exact count`() {
        val dense = StarMath.effectiveLayer(layer, 10.0)

        assertTrue(dense.cells > layer.cells)
        assertTrue(dense.density <= StarMath.MAX_CELL_DENSITY)
        val count = dense.density * dense.cells * dense.cells
        assertEquals(10 * layer.density * layer.cells * layer.cells, count, 1e-6 * count)
    }

    @Test
    fun `sharper stars are smaller and widen less`() {
        assertTrue(StarMath.sizePx(1.0) < StarMath.sizePx(0.0))
        assertTrue(StarMath.footprintWidening(1.0) < StarMath.footprintWidening(0.0))
        assertEquals(0.25, StarMath.footprintWidening(0.0), 1e-12) // §9.3 at the softest
        assertEquals(0.6, StarMath.sharpnessOf(StarMath.sizePx(0.6)), 1e-12)
    }
}
