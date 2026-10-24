package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.ThemeMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `a choice opens its dialog, and picking or dismissing closes it`() = runTest {
        val playback = reducer.reduce(SettingsState(), SettingsEvent.OnPlaybackClick)
        assertEquals(SettingsDialog.PLAYBACK, playback.dialog)
        assertNull(reducer.reduce(playback, SettingsEvent.OnPlaybackSelect(VideoPlayback.ONCE)).dialog)

        val theme = reducer.reduce(SettingsState(), SettingsEvent.OnThemeClick)
        assertEquals(SettingsDialog.THEME, theme.dialog)
        assertNull(reducer.reduce(theme, SettingsEvent.OnThemeSelect(ThemeMode.DARK)).dialog)
        assertNull(reducer.reduce(theme, SettingsEvent.OnDialogDismiss).dialog)
    }

    @Test
    fun `settings and storage follow their sources`() = runTest {
        var state = reducer.reduce(SettingsState(), SettingsEvent.OnSettingsChanged(AppSettings(dynamicColor = false)))
        state = reducer.reduce(state, SettingsEvent.OnStorageCounted(1_024))

        assertEquals(AppSettings(dynamicColor = false), state.settings)
        assertEquals(1_024, state.storageBytes)
    }

    @Test
    fun `a report waits until it is shared or fails`() = runTest {
        val writing = reducer.reduce(SettingsState(), SettingsEvent.OnSendReportClick)
        assertTrue(writing.isReportInProgress)

        assertFalse(reducer.reduce(writing, SettingsEvent.ShareReport("report.txt")).isReportInProgress)
        assertFalse(reducer.reduce(writing, SettingsEvent.OnReportFailed).isReportInProgress)
    }

    @Test
    fun `user actions change nothing by themselves`() = runTest {
        val state = SettingsState(isWallpaperSet = false)

        assertEquals(state, reducer.reduce(state, SettingsEvent.OnSetWallpaperClick))
        assertEquals(state, reducer.reduce(state, SettingsEvent.OnBackClick))
        assertEquals(state, reducer.reduce(state, SettingsEvent.OnResume))
        assertEquals(state, reducer.reduce(state, SettingsEvent.OnStorageClick))
        assertEquals(state, reducer.reduce(state, SettingsEvent.OnSaveBatteryChange(false)))
    }
}
