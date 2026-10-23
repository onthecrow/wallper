package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class EditorReducerTest {

    private val reducer = EditorReducer()

    @Test
    fun `dragging the start handle previews the start frame`() = runTest {
        val state = reducer.reduce(EditorState(), EditorEvent.OnTimeLineRangeChange(0.2f..1f))

        assertEquals(0.2f, state.seekPosition)
        assertEquals(0.2f..1f, state.timeLineRange)
    }

    @Test
    fun `dragging the end handle previews the end frame`() = runTest {
        val state = reducer.reduce(EditorState(), EditorEvent.OnTimeLineRangeChange(0f..0.7f))

        assertEquals(0.7f, state.seekPosition)
    }

    @Test
    fun `releasing the slider resumes playback`() = runTest {
        val dragging = EditorState(seekPosition = 0.5f)

        assertNull(reducer.reduce(dragging, EditorEvent.OnTimeLineRangeChangeFinished).seekPosition)
    }

    @Test
    fun `toggle, prepared media and export status land in the state`() = runTest {
        val media = PreparedMedia("temp", MediaType.VIDEO, MediaInfo(10, 20, 0, 1000))

        var state = reducer.reduce(EditorState(), EditorEvent.OnAdditionalProcessingToggle)
        state = reducer.reduce(state, EditorEvent.OnMediaPrepared(media))
        state = reducer.reduce(state, EditorEvent.OnExportStatusChanged(ExportStatus.InProgress(40)))

        assertEquals(true, state.isAdditionalProcessing)
        assertEquals(media, state.media)
        assertEquals(ExportStatus.InProgress(40), state.exportStatus)
    }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val failed = EditorState(exportStatus = ExportStatus.Error(IllegalStateException("encoder")))

        assertNull(reducer.reduce(failed, EditorEvent.OnErrorDismiss).exportStatus)
    }

    @Test
    fun `save click is a side effect only`() = runTest {
        val initial = EditorState()

        assertSame(initial, reducer.reduce(initial, EditorEvent.OnSaveClick(CropRect(0, 0, 1, 1))))
    }
}
