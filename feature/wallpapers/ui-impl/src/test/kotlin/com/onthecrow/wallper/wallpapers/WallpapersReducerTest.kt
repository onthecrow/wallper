package com.onthecrow.wallper.wallpapers

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
    fun `user actions do not touch the state`() = runTest {
        val initial = WallpapersState()

        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnWallpaperClick(1)))
        assertSame(initial, reducer.reduce(initial, WallpapersEvent.OnMediaPicked("content://x")))
    }
}
