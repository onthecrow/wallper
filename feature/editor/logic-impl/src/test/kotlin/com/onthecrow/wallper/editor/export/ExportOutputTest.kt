package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.editor.domain.model.ExportOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ExportOutputTest {

    private val output = ExportOutput(width = 1728, height = 3840, durationMs = 12_000, codec = "H.264")

    @Test
    fun `an encoder's smaller height keeps the proportions`() {
        assertEquals(output.copy(width = 972, height = 2160), output.withHeight(2160))
    }

    @Test
    fun `the same height or none changes nothing`() {
        assertSame(output, output.withHeight(3840))
        assertSame(output, output.withHeight(0))
    }
}
