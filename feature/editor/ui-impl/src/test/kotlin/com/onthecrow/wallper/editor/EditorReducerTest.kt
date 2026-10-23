package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorReducerTest {

    private val reducer = EditorReducer()

    @Test
    fun `prepared media is kept whole until a handle moves`() = runTest {
        val media = PreparedMedia("temp", MediaType.VIDEO, MediaInfo(10, 20, 0, 16_000))

        val state = reducer.reduce(EditorState(), EditorEvent.OnMediaPrepared(media))

        assertEquals(ClipRange(0, 16_000), state.trim)
    }

    @Test
    fun `moved handles land in the trim`() = runTest {
        val state = reducer.reduce(EditorState(), EditorEvent.OnTrimChange(ClipRange(2_000, 9_000)))

        assertEquals(ClipRange(2_000, 9_000), state.trim)
    }

    @Test
    fun `a preview that can't play shows its error`() = runTest {
        val error = IllegalStateException("file gone")

        val state = reducer.reduce(EditorState(), EditorEvent.OnPreviewFailed(error))

        assertEquals(ExportStatus.Error(error), state.exportStatus)
    }

    @Test
    fun `the preview plays until paused, and plays again`() = runTest {
        val paused = reducer.reduce(EditorState(), EditorEvent.OnPlayPauseClick)

        assertTrue(EditorState().isPlaying)
        assertFalse(paused.isPlaying)
        assertTrue(reducer.reduce(paused, EditorEvent.OnPlayPauseClick).isPlaying)
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
