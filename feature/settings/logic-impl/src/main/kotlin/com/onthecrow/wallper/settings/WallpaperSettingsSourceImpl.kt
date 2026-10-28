package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.engine.WallpaperSettings
import com.onthecrow.wallper.core.engine.WallpaperSettingsSource
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What the wallpaper service needs of the settings: those of video wallpapers. */
internal class WallpaperSettingsSourceImpl @Inject constructor(
    appSettings: AppSettingsSource,
) : WallpaperSettingsSource {
    override val settings: Flow<WallpaperSettings> = appSettings.settings
        .map { settings ->
            WallpaperSettings(
                playback = settings.videoPlayback,
                pausesToSaveBattery = settings.pauseVideoToSaveBattery,
                scrollsWithPages = settings.scrollVideoWithPages,
            )
        }
        .distinctUntilChanged()
}
