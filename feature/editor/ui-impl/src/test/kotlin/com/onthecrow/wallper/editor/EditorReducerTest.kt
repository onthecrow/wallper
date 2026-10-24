package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.editor.domain.model.EditedWallpaper
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
    private val media = PreparedMedia("temp", MediaType.VIDEO, MediaInfo(10, 20, 0, 16_000))
    private val crop = CropRect(0, 0, 1, 1)
    private val prepared = EditorState(media = media, trim = ClipRange(0, 16_000))

    @Test
    fun `prepared media is kept whole until a handle moves`() = runTest {
        val state = reducer.reduce(EditorState(), EditorEvent.OnMediaPrepared(media))

        assertEquals(ClipRange(0, 16_000), state.trim)
    }

    @Test
    fun `an edited wallpaper starts from its trim`() = runTest {
        val edited = media.copy(edited = EditedWallpaper(3, "thumbnail", crop, ClipRange(2_000, 9_000)))

        val state = reducer.reduce(EditorState(), EditorEvent.OnMediaPrepared(edited))

        assertEquals(ClipRange(2_000, 9_000), state.trim)
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
    fun `the preview waits for play, and pauses again`() = runTest {
        val playing = reducer.reduce(EditorState(), EditorEvent.OnPlayPauseClick)

        assertFalse(EditorState().isPlaying)
        assertTrue(playing.isPlaying)
        assertFalse(reducer.reduce(playing, EditorEvent.OnPlayPauseClick).isPlaying)
    }

    @Test
    fun `the preview stops at the end of the kept part`() = runTest {
        val playing = prepared.copy(isPlaying = true)

        assertFalse(reducer.reduce(playing, EditorEvent.OnPlaybackEnded).isPlaying)
    }

    @Test
    fun `the start moves in to the playhead`() = runTest {
        val state = reducer.reduce(prepared.copy(trim = ClipRange(2_000, 9_000)), EditorEvent.OnSetTrimStartClick(4_500))

        assertEquals(ClipRange(4_500, 9_000), state.trim)
    }

    @Test
    fun `the start stays unless the playhead is inside the kept part and leaves a second of it`() = runTest {
        val trimmed = prepared.copy(trim = ClipRange(2_000, 9_000))

        listOf(1_000L, 2_000, 8_500, 9_000, 12_000).forEach { positionMs ->
            assertSame(trimmed, reducer.reduce(trimmed, EditorEvent.OnSetTrimStartClick(positionMs)), "at $positionMs")
        }
        assertEquals(ClipRange(8_000, 9_000), reducer.reduce(trimmed, EditorEvent.OnSetTrimStartClick(8_000)).trim)
    }

    @Test
    fun `the end moves in to the playhead`() = runTest {
        val state = reducer.reduce(prepared.copy(trim = ClipRange(2_000, 9_000)), EditorEvent.OnSetTrimEndClick(6_000))

        assertEquals(ClipRange(2_000, 6_000), state.trim)
    }

    @Test
    fun `the end stays unless the playhead is inside the kept part and leaves a second of it`() = runTest {
        val trimmed = prepared.copy(trim = ClipRange(2_000, 9_000))

        listOf(200L, 2_000, 2_500, 9_000, 12_000).forEach { positionMs ->
            assertSame(trimmed, reducer.reduce(trimmed, EditorEvent.OnSetTrimEndClick(positionMs)), "at $positionMs")
        }
        assertEquals(ClipRange(2_000, 3_000), reducer.reduce(trimmed, EditorEvent.OnSetTrimEndClick(3_000)).trim)
    }

    @Test
    fun `toggle, prepared media and export status land in the state`() = runTest {
        var state = reducer.reduce(EditorState(isProcessingInfoSeen = true), EditorEvent.OnOptimisedToggle)
        state = reducer.reduce(state, EditorEvent.OnMediaPrepared(media))
        state = reducer.reduce(state, EditorEvent.OnExportStatusChanged(ExportStatus.InProgress(40)))

        assertEquals(true, state.isOptimised)
        assertEquals(media, state.media)
        assertEquals(ExportStatus.InProgress(40), state.exportStatus)
    }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val failed = EditorState(exportStatus = ExportStatus.Error(IllegalStateException("encoder")))

        assertNull(reducer.reduce(failed, EditorEvent.OnErrorDismiss).exportStatus)
    }

    @Test
    fun `save starts right away, original or optimised`() = runTest {
        assertEquals(
            SaveRequest(media, crop, ClipRange(0, 16_000), isOptimised = false),
            reducer.reduce(prepared, EditorEvent.OnSaveClick(crop)).save,
        )
        assertEquals(
            SaveRequest(media, crop, ClipRange(0, 16_000), isOptimised = true),
            reducer.reduce(prepared.copy(isOptimised = true), EditorEvent.OnSaveClick(crop)).save,
        )
    }

    @Test
    fun `the first switch to optimised waits for the explanation`() = runTest {
        val state = reducer.reduce(prepared, EditorEvent.OnOptimisedToggle)

        assertFalse(state.isOptimised)
        assertEquals(ProcessingInfo(optimisesOnConfirm = true), state.processingInfo)
        assertTrue(state.isProcessingInfoSeen)
    }

    @Test
    fun `OK on the explanation switches to optimised`() = runTest {
        val waiting = reducer.reduce(prepared, EditorEvent.OnOptimisedToggle)

        val state = reducer.reduce(waiting, EditorEvent.OnProcessingInfoConfirm)

        assertNull(state.processingInfo)
        assertTrue(state.isOptimised)
    }

    @Test
    fun `dismissing the explanation stays original, and the next switch doesn't wait`() = runTest {
        val waiting = reducer.reduce(prepared, EditorEvent.OnOptimisedToggle)

        val dismissed = reducer.reduce(waiting, EditorEvent.OnProcessingInfoDismiss)
        assertNull(dismissed.processingInfo)
        assertFalse(dismissed.isOptimised)

        val switched = reducer.reduce(dismissed, EditorEvent.OnOptimisedToggle)
        assertNull(switched.processingInfo)
        assertTrue(switched.isOptimised)
    }

    @Test
    fun `the question mark shows the explanation, and then the switch doesn't wait`() = runTest {
        val shown = reducer.reduce(prepared, EditorEvent.OnProcessingInfoClick)
        assertEquals(ProcessingInfo(optimisesOnConfirm = false), shown.processingInfo)

        var state = reducer.reduce(shown, EditorEvent.OnProcessingInfoConfirm)
        assertNull(state.processingInfo)
        assertFalse(state.isOptimised)

        state = reducer.reduce(state, EditorEvent.OnOptimisedToggle)
        assertNull(state.processingInfo)
        assertTrue(state.isOptimised)
    }

    @Test
    fun `back to original never waits`() = runTest {
        val state = reducer.reduce(prepared.copy(isOptimised = true), EditorEvent.OnOptimisedToggle)

        assertFalse(state.isOptimised)
        assertNull(state.processingInfo)
    }

    @Test
    fun `an explanation seen in an earlier editor doesn't hold the switch`() = runTest {
        val seen = reducer.reduce(prepared, EditorEvent.OnProcessingInfoSeenLoaded(true))

        val state = reducer.reduce(seen, EditorEvent.OnOptimisedToggle)

        assertNull(state.processingInfo)
        assertTrue(state.isOptimised)
    }

    @Test
    fun `the loaded flag doesn't hide an explanation already shown`() = runTest {
        val shown = reducer.reduce(prepared, EditorEvent.OnProcessingInfoClick)

        assertTrue(reducer.reduce(shown, EditorEvent.OnProcessingInfoSeenLoaded(false)).isProcessingInfoSeen)
    }

    @Test
    fun `nothing is saved before the media is ready, or twice`() = runTest {
        val loading = EditorState()
        assertSame(loading, reducer.reduce(loading, EditorEvent.OnSaveClick(crop)))

        val saving = reducer.reduce(prepared, EditorEvent.OnSaveClick(crop))
        assertSame(saving, reducer.reduce(saving, EditorEvent.OnSaveClick(CropRect(0, 0, 2, 2))))
    }

    @Test
    fun `a failed save can be tried again, a successful one stays done`() = runTest {
        val saving = reducer.reduce(prepared, EditorEvent.OnSaveClick(crop))

        val failed = reducer.reduce(saving, EditorEvent.OnExportStatusChanged(ExportStatus.Error(IllegalStateException("encoder"))))
        assertNull(failed.save)

        val saved = reducer.reduce(saving, EditorEvent.OnExportStatusChanged(ExportStatus.Success))
        assertEquals(saving.save, saved.save)
        assertSame(saved, reducer.reduce(saved, EditorEvent.OnSaveClick(crop)))
    }
}
