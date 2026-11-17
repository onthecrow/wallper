package com.onthecrow.wallper.wallpapers.group

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.model.SlideTransition
import com.onthecrow.wallper.core.model.TransitionEffect
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
    /** A member's pencil: its editor. */
    data class OnMemberEditClick(val id: Int) : GroupEditorEvent
    /** A built-in member's pencil: its settings. */
    data class OnMemberSettingsClick(val wallpaper: BuiltInWallpaper) : GroupEditorEvent
    /** A member dragged to another place: the members in their new order. */
    data class OnMembersReorder(val memberIds: List<Int>) : GroupEditorEvent
    data class OnDurationPick(val duration: SlideDuration) : GroupEditorEvent
    /** Opens the transition's step, where the members play through it. */
    data object OnTransitionClick : GroupEditorEvent
    /** Back from the transition's step to the members, with the transition chosen there. */
    data object OnTransitionClose : GroupEditorEvent
    data class OnTransitionEffectPick(val effect: TransitionEffect) : GroupEditorEvent
    data class OnTransitionDurationPick(val durationMs: Long) : GroupEditorEvent
    data object OnSaveClick : GroupEditorEvent
    /** Leaves without saving. */
    data object OnCloseClick : GroupEditorEvent

    // Internal events
    data class OnWallpapersChanged(val items: List<WallpaperItem>, val showsBuiltIns: Boolean) : GroupEditorEvent
    data class OnBuiltInPreviewLoaded(val wallpaper: BuiltInWallpaper, val path: String) : GroupEditorEvent
    data class OnGroupLoaded(
        val memberIds: List<Int>,
        val slideDuration: SlideDuration,
        val transition: SlideTransition,
    ) : GroupEditorEvent
    data class OnTransitionsOffered(val offered: Boolean) : GroupEditorEvent
    data class OnPreviewSlidesLoaded(val slides: List<WallpaperSpec>) : GroupEditorEvent
    data class OnSwipeHintLoaded(val shows: Boolean) : GroupEditorEvent

    // One-off effects, handled by the screen
    data object OnSaveFailed : GroupEditorEvent
}
