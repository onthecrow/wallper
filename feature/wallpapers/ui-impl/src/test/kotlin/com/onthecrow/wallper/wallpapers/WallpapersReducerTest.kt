package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WallpapersReducerTest {

    private val reducer = WallpapersReducer()

    @Test
    fun `wallpapers change replaces the list`() = runTest {
        val items = listOf(WallpaperItem(1, "a", true))

        val state = reducer.reduce(WallpapersState(), WallpapersEvent.OnWallpapersChanged(items))

        assertEquals(items, state.items)
    }

    @Test
    fun `load failure keeps the list and shows the error`() = runTest {
        val initial = WallpapersState(items = listOf(WallpaperItem(1, "a", false)))

        val state = reducer.reduce(initial, WallpapersEvent.OnWallpapersLoadFailed("boom"))

        assertEquals(initial.items, state.items)
        assertEquals("boom", state.error)
    }

    @Test
    fun `a rendered preview becomes the built-in's picture and survives list updates`() = runTest {
        val blackHole = WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE)
        val media = WallpaperItem(2, "a", true)
        val listed = reducer.reduce(WallpapersState(), WallpapersEvent.OnWallpapersChanged(listOf(blackHole, media)))

        val loaded = reducer.reduce(
            listed,
            WallpapersEvent.OnBuiltInPreviewLoaded(BuiltInWallpaper.BLACK_HOLE, "previews/black_hole.png"),
        )
        assertEquals(listOf(blackHole.copy(picturePath = "previews/black_hole.png"), media), loaded.items)

        // A new list from the database has no preview path of its own.
        val activated = listOf(blackHole.copy(isActive = true), media.copy(isActive = false))
        val updated = reducer.reduce(loaded, WallpapersEvent.OnWallpapersChanged(activated))
        assertEquals("previews/black_hole.png", updated.items.first().picturePath)
        assertEquals(listOf(true, false), updated.items.map { it.isActive })
    }

    @Test
    fun `a delete waits for its confirmation`() = runTest {
        val asked = reducer.reduce(WallpapersState(), WallpapersEvent.OnDeleteClick(3))
        assertEquals(3, asked.deleteCandidate)

        assertEquals(null, reducer.reduce(asked, WallpapersEvent.OnDeleteConfirm(3)).deleteCandidate)
        assertEquals(null, reducer.reduce(asked, WallpapersEvent.OnDeleteDismiss).deleteCandidate)
    }

    @Test
    fun `the plus asks what to add, and any answer closes the question`() = runTest {
        val asked = reducer.reduce(WallpapersState(), WallpapersEvent.OnAddClick)
        assertEquals(true, asked.isAddDialogShown)

        listOf(WallpapersEvent.OnAddDismiss, WallpapersEvent.OnAddMediaClick, WallpapersEvent.OnAddSeriesClick).forEach {
            assertEquals(false, reducer.reduce(asked, it).isAddDialogShown)
        }
    }

    @Test
    fun `a series' picture waits for its cover's previews, and its made picture survives list updates`() = runTest {
        val group = WallpaperItem(
            5, null, false, isGroup = true,
            cover = listOf(WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE), WallpaperItem(2, "a", false)),
        )
        val listed = reducer.reduce(WallpapersState(), WallpapersEvent.OnWallpapersChanged(listOf(group)))
        assertEquals(emptyMap(), listed.seriesCovers())

        val loaded = reducer.reduce(listed, WallpapersEvent.OnBuiltInPreviewLoaded(BuiltInWallpaper.BLACK_HOLE, "previews/bh.png"))
        assertEquals(mapOf(5 to listOf<String?>("previews/bh.png", "a")), loaded.seriesCovers())

        val made = reducer.reduce(loaded, WallpapersEvent.OnSeriesPictureMade(5, "series/5.webp"))
        assertEquals("series/5.webp", made.items.single().picturePath)

        val updated = reducer.reduce(made, WallpapersEvent.OnWallpapersChanged(listOf(group.copy(isActive = true))))
        assertEquals("series/5.webp", updated.items.single().picturePath)
        assertEquals(made.seriesCovers(), updated.seriesCovers())
    }

    @Test
    fun `a built-in that can't be rendered leaves its part of a series empty`() = runTest {
        val group = WallpaperItem(5, null, false, isGroup = true, cover = listOf(WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE)))
        val listed = reducer.reduce(WallpapersState(), WallpapersEvent.OnWallpapersChanged(listOf(group)))

        val loaded = reducer.reduce(listed, WallpapersEvent.OnBuiltInPreviewLoaded(BuiltInWallpaper.BLACK_HOLE, null))

        assertEquals(mapOf(5 to listOf<String?>(null)), loaded.seriesCovers())
    }

    @Test
    fun `user actions do not touch the state`() = runTest {
        val initial = WallpapersState()

        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnWallpaperClick(1)))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnEditClick(1)))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnGroupEditClick(1)))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnMediaPicked("content://x")))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnBuiltInSettingsClick(BuiltInWallpaper.BLACK_HOLE)))
    }
}
