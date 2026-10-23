package com.onthecrow.wallper.editor.preview

import com.onthecrow.wallper.core.model.ClipRange
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPlayerTest {

    private val trim = ClipRange(startMs = 2_000, endMs = 8_000)

    @Test
    fun `playback inside the kept part goes on`() {
        assertFalse(restartsLoop(positionMs = 2_000, trim = trim, ended = false))
        assertFalse(restartsLoop(positionMs = 7_999, trim = trim, ended = false))
    }

    @Test
    fun `playback starts the kept part over at its end and at the end of the video`() {
        assertTrue(restartsLoop(positionMs = 8_000, trim = trim, ended = false))
        assertTrue(restartsLoop(positionMs = 5_000, trim = trim, ended = true))
    }

    @Test
    fun `playback before the kept part jumps to its start`() {
        // A player made anew after the activity is recreated starts at 0.
        assertTrue(restartsLoop(positionMs = 0, trim = trim, ended = false))
    }

    @Test
    fun `without a known length the whole video loops`() {
        val whole = ClipRange(startMs = 0, endMs = 0)

        assertFalse(restartsLoop(positionMs = 60_000, trim = whole, ended = false))
        assertTrue(restartsLoop(positionMs = 60_000, trim = whole, ended = true))
    }
}
