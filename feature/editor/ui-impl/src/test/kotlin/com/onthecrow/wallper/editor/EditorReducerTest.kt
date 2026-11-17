package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.designsystem.component.colorpicker.HsvColor
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.Tint
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

    /** Half-transparent pure green: 0x8000FF00. */
    private val green = HsvColor(hue = 120f, saturation = 1f, value = 1f, alpha = 128 / 255f)

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
    fun `reset brings back the whole video and the original`() = runTest {
        val edited = prepared.copy(trim = ClipRange(2_000, 9_000), isOptimised = true)
        assertTrue(edited.hasEdits)

        val state = reducer.reduce(edited, EditorEvent.OnResetClick)

        assertEquals(ClipRange(0, 16_000), state.trim)
        assertFalse(state.isOptimised)
        assertFalse(state.hasEdits)
    }

    @Test
    fun `reset brings an edited wallpaper back to its saved trim`() = runTest {
        val saved = media.copy(edited = EditedWallpaper(3, "thumbnail", crop, ClipRange(2_000, 9_000)))
        val opened = reducer.reduce(EditorState(), EditorEvent.OnMediaPrepared(saved))
        assertFalse(opened.hasEdits)

        val state = reducer.reduce(opened.copy(trim = ClipRange(0, 16_000)), EditorEvent.OnResetClick)

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
        state = reducer.reduce(state, EditorEvent.OnSaveClick(crop))
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
            SaveRequest(media, crop, ClipRange(0, 16_000), isOptimised = false, tint = null),
            reducer.reduce(prepared, EditorEvent.OnSaveClick(crop)).save,
        )
        assertEquals(
            SaveRequest(media, crop, ClipRange(0, 16_000), isOptimised = true, tint = null),
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
    fun `a new wallpaper has no tint, and the first switch darkens it by a third`() = runTest {
        assertNull(prepared.tint.tint)

        val state = reducer.reduce(prepared, EditorEvent.OnTintToggle)

        assertEquals(Tint(0x4D000000), state.tint.tint)
        assertTrue(state.hasEdits)
    }

    @Test
    fun `the tint's switch keeps the colour for the next time`() = runTest {
        val picked = reducer.reduce(prepared, EditorEvent.OnTintColorChange(green))

        val off = reducer.reduce(picked, EditorEvent.OnTintToggle)
        assertNull(off.tint.tint)
        assertFalse(off.hasEdits)

        assertEquals(Tint(green.toArgb()), reducer.reduce(off, EditorEvent.OnTintToggle).tint.tint)
    }

    @Test
    fun `picking a colour switches the tint on`() = runTest {
        val state = reducer.reduce(prepared, EditorEvent.OnTintColorChange(green))

        assertEquals(TintChoice(isOn = true, color = green), state.tint)
        assertEquals(Tint(0x8000FF00.toInt()), state.tint.tint)
    }

    @Test
    fun `a fully transparent tint is none`() = runTest {
        val state = reducer.reduce(prepared, EditorEvent.OnTintColorChange(green.copy(alpha = 0f)))

        assertTrue(state.tint.isOn)
        assertNull(state.tint.tint)
        assertFalse(state.hasEdits)
    }

    @Test
    fun `an edited wallpaper starts from its tint, the picker at its colour`() = runTest {
        val saved = media.copy(edited = EditedWallpaper(3, "thumbnail", crop, ClipRange(0, 16_000), Tint(0x8000FF00.toInt())))

        val state = reducer.reduce(EditorState(), EditorEvent.OnMediaPrepared(saved))

        assertTrue(state.tint.isOn)
        assertEquals(Tint(0x8000FF00.toInt()), state.tint.tint)
        assertEquals(120f, state.tint.color.hue)
        assertFalse(state.hasEdits)
    }

    @Test
    fun `switching a saved tint off is an edit, and reset brings it back`() = runTest {
        val saved = media.copy(edited = EditedWallpaper(3, "thumbnail", crop, ClipRange(0, 16_000), Tint(0x8000FF00.toInt())))
        val opened = reducer.reduce(EditorState(), EditorEvent.OnMediaPrepared(saved))

        val off = reducer.reduce(opened, EditorEvent.OnTintToggle)
        assertTrue(off.hasEdits)

        val state = reducer.reduce(off, EditorEvent.OnResetClick)
        assertEquals(opened.tint, state.tint)
        assertFalse(state.hasEdits)
    }

    @Test
    fun `reset takes a new wallpaper's tint off and leaves the picker open`() = runTest {
        val tinted = reducer.reduce(prepared.copy(isTintOpen = true), EditorEvent.OnTintColorChange(green))

        val state = reducer.reduce(tinted, EditorEvent.OnResetClick)

        assertEquals(TintChoice(), state.tint)
        assertTrue(state.isTintOpen)
    }

    @Test
    fun `the tint button opens the picker and closes it again, and back only closes it`() = runTest {
        val open = reducer.reduce(prepared, EditorEvent.OnTintClick)
        assertTrue(open.isTintOpen)

        assertFalse(reducer.reduce(open, EditorEvent.OnTintClick).isTintOpen)
        assertFalse(reducer.reduce(open, EditorEvent.OnTintClose).isTintOpen)
        assertFalse(reducer.reduce(prepared, EditorEvent.OnTintClose).isTintOpen)
    }

    @Test
    fun `there is nothing to tint before the media is ready`() = runTest {
        assertFalse(reducer.reduce(EditorState(), EditorEvent.OnTintClick).isTintOpen)
    }

    @Test
    fun `the tint is saved with the wallpaper, and none while it is off`() = runTest {
        val tinted = reducer.reduce(prepared, EditorEvent.OnTintColorChange(green))
        assertEquals(Tint(0x8000FF00.toInt()), reducer.reduce(tinted, EditorEvent.OnSaveClick(crop)).save?.tint)

        val off = reducer.reduce(tinted, EditorEvent.OnTintToggle)
        assertNull(reducer.reduce(off, EditorEvent.OnSaveClick(crop)).save?.tint)
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

    @Test
    fun `a cancelled export closes its dialog for good and the save can start again`() = runTest {
        val saving = reducer.reduce(prepared.copy(isOptimised = true), EditorEvent.OnSaveClick(crop))
        val exporting = reducer.reduce(saving, EditorEvent.OnExportStatusChanged(ExportStatus.InProgress(30)))

        val cancelled = reducer.reduce(exporting, EditorEvent.OnExportCancel)
        assertNull(cancelled.save)
        assertNull(cancelled.exportStatus)

        // What the export sent before it stopped.
        assertSame(cancelled, reducer.reduce(cancelled, EditorEvent.OnExportStatusChanged(ExportStatus.InProgress(31))))
        assertSame(cancelled, reducer.reduce(cancelled, EditorEvent.OnExportStatusChanged(ExportStatus.Success)))
        assertEquals(saving.save, reducer.reduce(cancelled, EditorEvent.OnSaveClick(crop)).save)
    }

    @Test
    fun `only an export under way can be cancelled`() = runTest {
        val saving = reducer.reduce(prepared, EditorEvent.OnSaveClick(crop))
        val saved = reducer.reduce(saving, EditorEvent.OnExportStatusChanged(ExportStatus.Success))

        assertSame(saving, reducer.reduce(saving, EditorEvent.OnExportCancel))
        assertSame(saved, reducer.reduce(saved, EditorEvent.OnExportCancel))
    }
}
