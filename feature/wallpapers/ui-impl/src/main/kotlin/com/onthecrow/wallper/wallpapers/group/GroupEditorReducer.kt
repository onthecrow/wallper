package com.onthecrow.wallper.wallpapers.group

import com.onthecrow.wallper.core.mvi.Reducer
import com.onthecrow.wallper.wallpapers.model.withPreviews
import javax.inject.Inject

internal class GroupEditorReducer @Inject constructor() : Reducer<GroupEditorState, GroupEditorEvent> {
    override suspend fun reduce(state: GroupEditorState, event: GroupEditorEvent): GroupEditorState =
        when (event) {
            is GroupEditorEvent.OnWallpapersChanged -> state.copy(
                wallpapers = event.items.withPreviews(state.builtInPreviews),
                showsBuiltIns = event.showsBuiltIns,
            )
            is GroupEditorEvent.OnBuiltInPreviewLoaded -> {
                val previews = state.builtInPreviews + (event.wallpaper to event.path)
                state.copy(wallpapers = state.wallpapers.withPreviews(previews), builtInPreviews = previews)
            }
            is GroupEditorEvent.OnGroupLoaded -> state.copy(
                members = event.memberIds,
                slideDuration = event.slideDuration,
                isLoaded = true,
            )
            is GroupEditorEvent.OnWallpaperClick -> state.copy(
                picked = if (event.id in state.picked) state.picked - event.id else state.picked + event.id,
            )
            GroupEditorEvent.OnPickConfirm -> state.confirmPick()
            GroupEditorEvent.OnPickCancel -> state.copy(step = GroupEditorStep.ARRANGE, picked = emptySet())
            GroupEditorEvent.OnAddClick -> state.copy(step = GroupEditorStep.PICK, picked = state.members.toSet())
            is GroupEditorEvent.OnMemberRemove -> state.copy(members = state.members - event.id)
            is GroupEditorEvent.OnDurationPick -> state.copy(slideDuration = event.duration)
            GroupEditorEvent.OnSaveClick -> if (state.canSave) {
                state.copy(save = GroupSaveRequest(state.memberItems.map { it.id }, state.slideDuration))
            } else {
                state
            }
            GroupEditorEvent.OnSaveFailed -> state.copy(save = null)
            GroupEditorEvent.OnCloseClick -> state
        }

    /** Members keep their order; the newly picked ones follow, in the list's order. */
    private fun GroupEditorState.confirmPick(): GroupEditorState {
        if (picked.isEmpty()) return this
        val kept = members.filter { it in picked }
        val added = pickable.map { it.id }.filter { it in picked && it !in kept }
        return copy(step = GroupEditorStep.ARRANGE, members = kept + added, picked = emptySet())
    }
}
