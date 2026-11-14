package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.engine.WallpaperSettings
import com.onthecrow.wallper.core.engine.WallpaperSettingsSource
import com.onthecrow.wallper.core.model.SeriesSwitch
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.settings.domain.DebugBuild
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What the wallpaper service needs of the settings. A build that isn't debuggable always cuts between a
 * series' slides, whatever is stored: the other ways are only tried out.
 */
internal class WallpaperSettingsSourceImpl @Inject constructor(
    appSettings: AppSettingsSource,
    debugBuild: DebugBuild,
) : WallpaperSettingsSource {
    private val isDebuggable = debugBuild.isDebuggable()

    override val settings: Flow<WallpaperSettings> = appSettings.settings
        .map { settings ->
            WallpaperSettings(
                playback = settings.videoPlayback,
                pausesToSaveBattery = settings.pauseVideoToSaveBattery,
                scrollsWithPages = settings.scrollVideoWithPages,
                seriesSwitch = if (isDebuggable) settings.seriesSwitch else SeriesSwitch.CUT,
            )
        }
        .distinctUntilChanged()
}
