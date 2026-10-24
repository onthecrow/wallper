package com.onthecrow.wallper.settings.domain

import com.onthecrow.wallper.core.model.VideoPlayback
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

    @Test
    fun `defaults are stored as nothing`() {
        assertEquals("{}", AppSettings().encode())
    }

    @Test
    fun `changed settings round-trip`() {
        val settings = AppSettings(
            videoPlayback = VideoPlayback.ONCE,
            pauseVideoToSaveBattery = false,
            theme = ThemeMode.DARK,
            dynamicColor = false,
        )

        assertEquals(settings, AppSettings.decode(settings.encode()))
    }

    @Test
    fun `what this version doesn't know is skipped`() {
        val settings = AppSettings.decode("""{"theme":"SEPIA","fromTheFuture":1,"activateNewWallpapers":false}""")

        assertEquals(AppSettings(activateNewWallpapers = false), settings)
    }

    @Test
    fun `malformed text gives the defaults`() {
        assertEquals(AppSettings(), AppSettings.decode("{not json"))
    }
}
