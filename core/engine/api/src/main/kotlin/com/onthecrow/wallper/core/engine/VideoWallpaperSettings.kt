package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.VideoPlayback
import kotlinx.coroutines.flow.Flow

/** What the user chose about video wallpapers. */
data class VideoWallpaperSettings(
    val playback: VideoPlayback = VideoPlayback.LOOP,
    /** Pause the video in Battery Saver and when the phone is hot; it stays on its frame. */
    val pausesToSaveBattery: Boolean = true,
    /** Pan an original video along with the launcher's pages, within the parts of it the crop leaves out. */
    val scrollsWithPages: Boolean = true,
)

/** Port the wallpaper service reads the settings from, in direct boot too. Implemented by the settings feature. */
interface VideoWallpaperSettingsSource {
    val settings: Flow<VideoWallpaperSettings>
}
