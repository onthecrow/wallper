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

    private fun settingsOf(stored: AppSettings) = WallpaperSettingsSourceImpl(
        appSettings = object : AppSettingsSource {
            override val settings = MutableStateFlow(stored)
            override fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
        },
    ).settings

    @Test
    fun `the wallpaper gets its settings from the app's`() = runTest {
        val stored = AppSettings(
            videoPlayback = VideoPlayback.ONCE,
            pauseVideoToSaveBattery = false,
            scrollVideoWithPages = false,
            seriesSwitch = SeriesSwitch.WARM_ENGINES,
        )
        val expected = WallpaperSettings(
            playback = VideoPlayback.ONCE,
            pausesToSaveBattery = false,
            scrollsWithPages = false,
            seriesSwitch = SeriesSwitch.WARM_ENGINES,
        )

        assertEquals(expected, settingsOf(stored).first())
    }

    @Test
    fun `a series changes on the shared GL thread unless something else is stored`() = runTest {
        assertEquals(SeriesSwitch.SHARED_GL, settingsOf(AppSettings()).first().seriesSwitch)
    }
}
