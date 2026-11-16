package com.onthecrow.wallper.core.model

import com.onthecrow.wallper.core.model.SeriesTimeline.Moment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SeriesTimelineTest {

    /** Three slides, each alone for 3 s, then 1 s into the next. */
    private val timeline = SeriesTimeline(slideCount = 3, holdMs = 3_000, transitionMs = 1_000)

    @Test
    fun `a round is every slide and the transition after it`() {
        assertEquals(4_000, timeline.slotMs)
        assertEquals(12_000, timeline.durationMs)
        assertEquals(4_000, timeline.holdStartOf(1))
        assertEquals(7_000, timeline.transitionStartOf(1))
    }

    @Test
    fun `a slide alone is as far into its time as since the transition that brought it in`() {
        assertEquals(Moment.Slide(index = 0, sinceShownMs = 1_000), timeline.at(0))
        assertEquals(Moment.Slide(index = 1, sinceShownMs = 2_500), timeline.at(5_500))
    }

    @Test
    fun `a transition blends the slide on screen into the next, both going on in their own time`() {
        assertEquals(
            Moment.Transition(from = 1, to = 2, progress = 0.25f, fromSinceShownMs = 4_250, toSinceShownMs = 250),
            timeline.at(7_250),
        )
    }

    @Test
    fun `the last slide blends into the first, whose time starts there`() {
        val last = timeline.at(11_500)
        assertEquals(Moment.Transition(from = 2, to = 0, progress = 0.5f, fromSinceShownMs = 4_500, toSinceShownMs = 500), last)
        // The round ends where the first slide's hold starts: half a transition later it is 500 ms further.
        assertEquals(Moment.Slide(index = 0, sinceShownMs = 1_000), timeline.at(12_000))
    }

    @Test
    fun `positions go round, before the start too`() {
        assertEquals(timeline.at(5_500), timeline.at(5_500 + 3 * 12_000))
        assertEquals(timeline.at(11_500), timeline.at(-500))
    }

    @Test
    fun `without a transition one slide follows the next`() {
        val cut = SeriesTimeline(slideCount = 2, holdMs = 3_000, transitionMs = 0)

        assertEquals(6_000, cut.durationMs)
        assertEquals(Moment.Slide(index = 0, sinceShownMs = 2_999), cut.at(2_999))
        assertEquals(Moment.Slide(index = 1, sinceShownMs = 0), cut.at(3_000))
    }

    @Test
    fun `a series of one has nothing to blend into`() {
        val single = SeriesTimeline(slideCount = 1, holdMs = 3_000, transitionMs = 1_000)

        assertEquals(3_000, single.durationMs)
        assertEquals(Moment.Slide(index = 0, sinceShownMs = 2_000), single.at(2_000))
        assertEquals(Moment.Slide(index = 0, sinceShownMs = 0), single.at(3_000))
    }

    @Test
    fun `a moment lists the slides it draws with their times`() {
        assertEquals(mapOf(1 to 4_250L, 2 to 250L), timeline.at(7_250).slides)
        assertEquals(mapOf(1 to 2_500L), timeline.at(5_500).slides)
    }

    @Test
    fun `a timeline needs slides`() {
        assertFailsWith<IllegalArgumentException> { SeriesTimeline(slideCount = 0, holdMs = 3_000, transitionMs = 0) }
    }
}
