package com.onthecrow.wallper.wallpapers.group

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.mvi.Event
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal sealed interface GroupEditorEvent : Event {
    // User actions
    /** Ticks or unticks a wallpaper on the pick step. */
    data class OnWallpaperClick(val id: Int) : GroupEditorEvent
    /** The pick is done: new members join at the end, unticked ones leave. */
    data object OnPickConfirm : GroupEditorEvent
    /** Back from adding to an existing set of members: nothing changes. */
    data object OnPickCancel : GroupEditorEvent
    /** Back to the pick step, the members ticked. */
    data object OnAddClick : GroupEditorEvent
    data class OnMemberRemove(val id: Int) : GroupEditorEvent
    data class OnDurationPick(val duration: SlideDuration) : GroupEditorEvent
    data object OnSaveClick : GroupEditorEvent
    /** Leaves without saving. */
    data object OnCloseClick : GroupEditorEvent

    // Internal events
    data class OnWallpapersChanged(val items: List<WallpaperItem>, val showsBuiltIns: Boolean) : GroupEditorEvent
    data class OnBuiltInPreviewLoaded(val wallpaper: BuiltInWallpaper, val path: String) : GroupEditorEvent
    data class OnGroupLoaded(val memberIds: List<Int>, val slideDuration: SlideDuration) : GroupEditorEvent
    data class OnSwipeHintLoaded(val shows: Boolean) : GroupEditorEvent

    // One-off effects, handled by the screen
    data object OnSaveFailed : GroupEditorEvent
}
