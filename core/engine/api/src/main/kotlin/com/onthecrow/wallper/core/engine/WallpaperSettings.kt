package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.VideoPlayback
import kotlinx.coroutines.flow.Flow

/** What the user chose about the wallpaper: mostly about videos. */
data class WallpaperSettings(
    val playback: VideoPlayback = VideoPlayback.LOOP,
    /** Pause the video in Battery Saver and when the phone is hot; it stays on its frame. */
    val pausesToSaveBattery: Boolean = true,
    /**
     * Follow the launcher's pages: an original video pans within what its crop leaves out, the black hole
     * turns. Off, the service tells every wallpaper there are no pages.
     */
    val scrollsWithPages: Boolean = true,
)

/** Port the wallpaper service reads the settings from, in direct boot too. Implemented by the settings feature. */
interface WallpaperSettingsSource {
    val settings: Flow<WallpaperSettings>
}
