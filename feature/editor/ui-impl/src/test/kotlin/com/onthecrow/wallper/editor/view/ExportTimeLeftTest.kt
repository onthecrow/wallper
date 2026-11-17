package com.onthecrow.wallper.editor.view

import com.onthecrow.wallper.editor.view.ExportTimeLeft.Estimate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportTimeLeftTest {

    @Test
    fun `nothing known is too early to tell`() {
        assertEquals(Estimate.Unknown, ExportTimeLeft.of(null))
    }

    @Test
    fun `seconds go up to the next five, minutes to the nearest`() {
        assertEquals(Estimate.Seconds(25), ExportTimeLeft.of(21_000))
        assertEquals(Estimate.Seconds(30), ExportTimeLeft.of(30_000))
        // 57 s: 60 is a minute.
        assertEquals(Estimate.Minutes(1), ExportTimeLeft.of(57_000))
        assertEquals(Estimate.Minutes(1), ExportTimeLeft.of(89_000))
        assertEquals(Estimate.Minutes(2), ExportTimeLeft.of(91_000))
    }

    @Test
    fun `the last seconds are almost done`() {
        assertEquals(Estimate.AlmostDone, ExportTimeLeft.of(0))
        assertEquals(Estimate.AlmostDone, ExportTimeLeft.of(4_999))
        assertEquals(Estimate.Seconds(5), ExportTimeLeft.of(5_000))
    }
}
