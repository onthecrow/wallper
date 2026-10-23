package com.onthecrow.wallper.wallpapers

import android.content.Intent
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.Event
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal sealed interface WallpapersEvent : Event {
    // User actions
    data class OnWallpaperClick(val id: Int) : WallpapersEvent
    data class OnDeleteClick(val id: Int) : WallpapersEvent
    data class OnBuiltInSettingsClick(val wallpaper: BuiltInWallpaper) : WallpapersEvent
    data class OnMediaPicked(val uri: String) : WallpapersEvent
    data object OnSettingsClick : WallpapersEvent

    // Internal events
    data class OnWallpapersChanged(val items: List<WallpaperItem>) : WallpapersEvent
    data class OnWallpapersLoadFailed(val message: String) : WallpapersEvent
    data class OnBuiltInPreviewLoaded(val wallpaper: BuiltInWallpaper, val path: String) : WallpapersEvent

    // One-off effects, handled by the screen
    data class OpenLiveWallpaperChooser(val intent: Intent) : WallpapersEvent
    data object OnUnsupportedMediaPicked : WallpapersEvent
}
