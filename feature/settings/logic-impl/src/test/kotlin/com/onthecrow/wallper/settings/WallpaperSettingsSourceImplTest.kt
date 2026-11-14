package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.engine.WallpaperSettings
import com.onthecrow.wallper.core.model.SeriesSwitch
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WallpaperSettingsSourceImplTest {

    private val stored = AppSettings(
        videoPlayback = VideoPlayback.ONCE,
        pauseVideoToSaveBattery = false,
        scrollVideoWithPages = false,
        seriesSwitch = SeriesSwitch.SHARED_GL,
    )

    private fun settingsOf(isDebuggable: Boolean) = WallpaperSettingsSourceImpl(
        appSettings = object : AppSettingsSource {
            override val settings = MutableStateFlow(stored)
            override fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
        },
        debugBuild = { isDebuggable },
    ).settings

    @Test
    fun `the wallpaper gets its settings from the app's`() = runTest {
        val expected = WallpaperSettings(
            playback = VideoPlayback.ONCE,
            pausesToSaveBattery = false,
            scrollsWithPages = false,
            seriesSwitch = SeriesSwitch.SHARED_GL,
        )

        assertEquals(expected, settingsOf(isDebuggable = true).first())
    }

    @Test
    fun `a build that isn't debuggable always cuts, whatever is stored`() = runTest {
        assertEquals(SeriesSwitch.CUT, settingsOf(isDebuggable = false).first().seriesSwitch)
    }
}
