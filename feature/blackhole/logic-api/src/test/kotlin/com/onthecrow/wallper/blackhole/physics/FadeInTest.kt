package com.onthecrow.wallper.blackhole.physics

import kotlin.test.Test
import kotlin.test.assertEquals

class FadeInTest {

    private val fade = FadeIn(durationNanos = 1_000)

    @Test
    fun `frames fade the picture in from the first one`() {
        assertEquals(0.0, fade.level(5_000, still = false))
        assertEquals(0.5, fade.level(5_500, still = false))
        assertEquals(1.0, fade.level(6_000, still = false))
        assertEquals(1.0, fade.level(60_000, still = false))
    }

    @Test
    fun `a still frame shows the picture whole, first or not`() {
        assertEquals(1.0, fade.level(5_000, still = true))

        val fading = FadeIn(durationNanos = 1_000)
        fading.level(5_000, still = false)
        assertEquals(1.0, fading.level(5_100, still = true))
    }

    @Test
    fun `frames after a still one don't fade again`() {
        fade.level(5_000, still = true)

        assertEquals(1.0, fade.level(5_016, still = false))
    }
}
