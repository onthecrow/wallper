package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.Event
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal sealed interface WallpapersEvent : Event {
    // User actions
    data class OnWallpaperClick(val id: Int) : WallpapersEvent
    data class OnEditClick(val id: Int) : WallpapersEvent
    /** A group's edit: its members. */
    data class OnGroupEditClick(val id: Int) : WallpapersEvent
    /** The «+»: asks what to add. */
    data object OnAddClick : WallpapersEvent
    data object OnAddDismiss : WallpapersEvent
    /** One wallpaper from the gallery: the screen opens the picker. */
    data object OnAddMediaClick : WallpapersEvent
    data object OnAddSlideshowClick : WallpapersEvent
    /** Asks first: the wallpaper's files go with it. */
    data class OnDeleteClick(val id: Int) : WallpapersEvent
    data class OnDeleteConfirm(val id: Int) : WallpapersEvent
    data object OnDeleteDismiss : WallpapersEvent
    data class OnBuiltInSettingsClick(val wallpaper: BuiltInWallpaper) : WallpapersEvent
    data class OnMediaPicked(val uri: String) : WallpapersEvent
    data object OnSettingsClick : WallpapersEvent

    // Internal events
    data class OnWallpapersChanged(val items: List<WallpaperItem>) : WallpapersEvent
    data class OnWallpapersLoadFailed(val message: String) : WallpapersEvent
    data class OnBuiltInPreviewLoaded(val wallpaper: BuiltInWallpaper, val path: String) : WallpapersEvent

    // One-off effects, handled by the screen
    data object OnUnsupportedMediaPicked : WallpapersEvent
}
