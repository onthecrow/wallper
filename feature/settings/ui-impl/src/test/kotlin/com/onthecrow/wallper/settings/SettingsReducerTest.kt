package com.onthecrow.wallper.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsReducerTest {

    private val reducer = SettingsReducer()

    @Test
    fun `the wallpaper is unknown until the first check, then follows every check`() = runTest {
        var state = SettingsState()
        assertNull(state.isWallpaperSet)

        state = reducer.reduce(state, SettingsEvent.OnWallpaperChecked(false))
        assertEquals(false, state.isWallpaperSet)

        state = reducer.reduce(state, SettingsEvent.OnWallpaperChecked(true))
        assertEquals(true, state.isWallpaperSet)
    }

    @Test
    fun `user actions change nothing by themselves`() = runTest {
        val state = SettingsState(isWallpaperSet = false)

        assertEquals(state, reducer.reduce(state, SettingsEvent.OnSetWallpaperClick))
        assertEquals(state, reducer.reduce(state, SettingsEvent.OnBackClick))
        assertEquals(state, reducer.reduce(state, SettingsEvent.OnResume))
    }
}
