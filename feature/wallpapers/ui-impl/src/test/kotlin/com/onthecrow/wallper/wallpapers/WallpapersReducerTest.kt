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
    fun `user actions do not touch the state`() = runTest {
        val initial = WallpapersState()

        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnWallpaperClick(1)))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnMediaPicked("content://x")))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnBuiltInSettingsClick(BuiltInWallpaper.BLACK_HOLE)))
    }
}
