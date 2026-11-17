package com.onthecrow.wallper.editor.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportPaceTest {

    @Test
    fun `the pace counts from the first percent, not from the start`() {
        val pace = ExportPace()
        assertNull(pace.remainingMs(progress = 0, elapsedMs = 500))
        // A slow start: 4 % after 2 s.
        assertNull(pace.remainingMs(progress = 4, elapsedMs = 2_000))

        // Then 1 % every 100 ms: 70 % to go.
        assertEquals(7_000, pace.remainingMs(progress = 30, elapsedMs = 4_600))
    }

    @Test
    fun `too early to tell until a few percent and a moment have gone`() {
        val pace = ExportPace()
        pace.remainingMs(progress = 10, elapsedMs = 1_000)

        assertNull(pace.remainingMs(progress = 12, elapsedMs = 5_000))
        assertNull(pace.remainingMs(progress = 20, elapsedMs = 2_000))
        assertEquals(64_000, pace.remainingMs(progress = 20, elapsedMs = 9_000))
    }

    @Test
    fun `done is nothing left`() {
        val pace = ExportPace()
        pace.remainingMs(progress = 50, elapsedMs = 1_000)

        assertEquals(0, pace.remainingMs(progress = 100, elapsedMs = 1_200))
    }
}
