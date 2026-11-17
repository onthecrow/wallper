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
    data object OnAddSeriesClick : WallpapersEvent
    /** Asks first: the wallpaper's files go with it. */
    data class OnDeleteClick(val id: Int) : WallpapersEvent
    data class OnDeleteConfirm(val id: Int) : WallpapersEvent
    data object OnDeleteDismiss : WallpapersEvent
    data class OnBuiltInSettingsClick(val wallpaper: BuiltInWallpaper) : WallpapersEvent
    data class OnMediaPicked(val uri: String) : WallpapersEvent
    data object OnSettingsClick : WallpapersEvent
    /** Back from the system's wallpaper picker, or from anywhere else the phone's wallpaper could have changed. */
    data object OnResume : WallpapersEvent

    // Internal events
    /** [items] as listed; [seriesCandidates] is how many a new series could pick from. */
    data class OnWallpapersChanged(val items: List<WallpaperItem>, val seriesCandidates: Int = 0) : WallpapersEvent
    data class OnWallpapersLoadFailed(val message: String) : WallpapersEvent
    /** [path] is null for a built-in this device can't render: none will come. */
    data class OnBuiltInPreviewLoaded(val wallpaper: BuiltInWallpaper, val path: String?) : WallpapersEvent
    data class OnSeriesPictureMade(val groupId: Int, val path: String) : WallpapersEvent
    data class OnWallpaperChecked(val isSet: Boolean) : WallpapersEvent

    // One-off effects, handled by the screen
    data object OnUnsupportedMediaPicked : WallpapersEvent
}
