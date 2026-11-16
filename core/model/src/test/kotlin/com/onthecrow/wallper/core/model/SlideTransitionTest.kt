package com.onthecrow.wallper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlideTransitionTest {

    @Test
    fun `a duration is kept in range, on a step`() {
        assertEquals(SlideTransition.MIN_DURATION_MS, SlideTransition.duration(0))
        assertEquals(SlideTransition.MAX_DURATION_MS, SlideTransition.duration(60_000))
        assertEquals(1_300, SlideTransition.duration(1_349))
        assertEquals(1_400, SlideTransition.duration(1_350))
        assertEquals(1_700, SlideTransition.duration(1_700))
    }

    @Test
    fun `every effect comes back from its key, and an unknown one is none`() {
        TransitionEffect.entries.forEach { effect -> assertEquals(effect, TransitionEffect.fromKey(effect.key)) }
        assertEquals(TransitionEffect.NONE, TransitionEffect.fromKey("page_curl"))
        assertEquals(TransitionEffect.NONE, TransitionEffect.fromKey(null))
    }

    @Test
    fun `no transition is the default`() {
        assertTrue(SlideTransition().isNone)
        assertEquals(SlideTransition.DEFAULT_DURATION_MS, SlideTransition.NONE.durationMs)
    }
}
