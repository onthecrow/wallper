package com.onthecrow.wallper.editor.preview

import com.onthecrow.wallper.core.model.ClipRange
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPlayerTest {

    private val trim = ClipRange(startMs = 2_000, endMs = 8_000)

    @Test
    fun `playback inside the kept part goes on`() {
        assertFalse(isAtTrimEnd(positionMs = 2_000, trim = trim, ended = false))
        assertFalse(isAtTrimEnd(positionMs = 7_999, trim = trim, ended = false))
    }

    @Test
    fun `playback stops at the end of the kept part and at the end of the video`() {
        assertTrue(isAtTrimEnd(positionMs = 8_000, trim = trim, ended = false))
        assertTrue(isAtTrimEnd(positionMs = 9_000, trim = trim, ended = false))
        assertTrue(isAtTrimEnd(positionMs = 5_000, trim = trim, ended = true))
    }

    @Test
    fun `playback before the kept part goes on into it`() {
        assertFalse(isAtTrimEnd(positionMs = 0, trim = trim, ended = false))
    }

    @Test
    fun `without a known length the whole video plays`() {
        val whole = ClipRange(startMs = 0, endMs = 0)

        assertFalse(isAtTrimEnd(positionMs = 60_000, trim = whole, ended = false))
        assertTrue(isAtTrimEnd(positionMs = 60_000, trim = whole, ended = true))
    }
}
