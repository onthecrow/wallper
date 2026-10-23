package com.onthecrow.wallper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipRangeTest {

    @Test
    fun `a clip with an end plays to it`() {
        assertEquals(5_000, ClipRange(startMs = 1_000, endMs = 5_000).endMsOrNull)
    }

    @Test
    fun `a clip cut without the video's length plays to the end`() {
        assertNull(ClipRange(startMs = 0, endMs = 0).endMsOrNull)
    }
}
