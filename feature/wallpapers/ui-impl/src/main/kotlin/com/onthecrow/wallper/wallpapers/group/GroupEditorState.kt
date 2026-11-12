package com.onthecrow.wallper.wallpapers.group

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.wallpapers.domain.SwipeHintMemory
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

/**
 * A group being made or edited: first the wallpapers are picked ([GroupEditorStep.PICK]), then arranged
 * ([GroupEditorStep.ARRANGE]): taken out, more added, the time each one shows chosen.
 */
internal data class GroupEditorState(
    val isNew: Boolean = true,
    val step: GroupEditorStep = GroupEditorStep.PICK,
    /** False until an edited group is read; a new one has nothing to read. */
    val isLoaded: Boolean = isNew,
    /** Every wallpaper that can be a member, in the list's order: no groups, no built-in this device can't draw. */
    val wallpapers: List<WallpaperItem> = emptyList(),
    /** Whether the user lists the built-ins: a hidden one is offered only while it is a member. */
    val showsBuiltIns: Boolean = true,
    /** Previews rendered so far, kept apart from [wallpapers] so that they survive list updates. */
    val builtInPreviews: Map<BuiltInWallpaper, String> = emptyMap(),
    /** The wallpapers ticked on the pick step. */
    val picked: Set<Int> = emptySet(),
    /** The group as it will be saved, in order. */
    val members: List<Int> = emptyList(),
    val slideDuration: SlideDuration = SlideDuration.DEFAULT,
    /**
     * Whether the members show the «swipe aside» hint: on the first few visits only ([SwipeHintMemory]). Null
     * until it is known: the members wait for it, as a list doesn't show what comes in above its first item.
     */
    val showsSwipeHint: Boolean? = null,
    /** The save under way, until it fails; the screen closes once it succeeds. */
    val save: GroupSaveRequest? = null,
) : State {

    /** What the pick step offers. */
    val pickable: List<WallpaperItem>
        get() = wallpapers.filter { it.builtIn == null || showsBuiltIns || it.id in members }

    /** The members that still exist, in order. */
    val memberItems: List<WallpaperItem>
        get() = members.mapNotNull { id -> wallpapers.firstOrNull { it.id == id } }

    val canSave: Boolean get() = memberItems.size >= MIN_MEMBERS && save == null

    /** The members can show: the group is read and so is whether the hint shows. */
    val isArrangeReady: Boolean get() = isLoaded && showsSwipeHint != null

    companion object {
        /** A series of one wallpaper would be that wallpaper. */
        const val MIN_MEMBERS = 2

        fun initial(groupId: Int?) = GroupEditorState(
            isNew = groupId == null,
            step = if (groupId == null) GroupEditorStep.PICK else GroupEditorStep.ARRANGE,
        )
    }
}

internal enum class GroupEditorStep { PICK, ARRANGE }

internal data class GroupSaveRequest(val memberIds: List<Int>, val slideDuration: SlideDuration)
