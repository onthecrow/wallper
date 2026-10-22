package com.onthecrow.wallper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaInfoTest {

    @Test
    fun `a portrait recording stored landscape is shown portrait`() {
        val info = MediaInfo(width = 1920, height = 1080, rotation = 90, durationMs = 0)

        assertEquals(1080, info.displayWidth)
        assertEquals(1920, info.displayHeight)
    }

    @Test
    fun `upside down keeps the stored size`() {
        val info = MediaInfo(width = 1920, height = 1080, rotation = 180, durationMs = 0)

        assertEquals(1920, info.displayWidth)
        assertEquals(1080, info.displayHeight)
    }
}
