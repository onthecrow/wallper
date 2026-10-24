package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.engine.VideoWallpaperSettings
import com.onthecrow.wallper.core.engine.VideoWallpaperSettingsSource
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What the wallpaper service needs of the settings: those of video wallpapers. */
internal class VideoWallpaperSettingsSourceImpl @Inject constructor(
    appSettings: AppSettingsSource,
) : VideoWallpaperSettingsSource {
    override val settings: Flow<VideoWallpaperSettings> = appSettings.settings
        .map { settings ->
            VideoWallpaperSettings(
                playback = settings.videoPlayback,
                pausesToSaveBattery = settings.pauseVideoToSaveBattery,
                scrollsWithPages = settings.scrollVideoWithPages,
            )
        }
        .distinctUntilChanged()
}
