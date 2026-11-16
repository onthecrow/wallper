package com.onthecrow.wallper.wallpapers.group

import com.onthecrow.wallper.core.model.SlideTransition
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
            is GroupEditorEvent.OnSwipeHintLoaded -> state.copy(showsSwipeHint = event.shows)
            is GroupEditorEvent.OnGroupLoaded -> state.copy(
                members = event.memberIds,
                slideDuration = event.slideDuration,
                transition = event.transition,
                isLoaded = true,
            )
            is GroupEditorEvent.OnTransitionsOffered -> state.copy(
                offersTransitions = event.offered,
                step = if (!event.offered && state.step == GroupEditorStep.TRANSITION) GroupEditorStep.ARRANGE else state.step,
            )
            // The members may have changed since the last time: they are read again.
            GroupEditorEvent.OnTransitionClick -> if (state.offersTransitions && state.step == GroupEditorStep.ARRANGE) {
                state.copy(step = GroupEditorStep.TRANSITION, previewSlides = null)
            } else {
                state
            }
            GroupEditorEvent.OnTransitionClose -> if (state.step == GroupEditorStep.TRANSITION) {
                state.copy(step = GroupEditorStep.ARRANGE, previewSlides = null)
            } else {
                state
            }
            is GroupEditorEvent.OnTransitionEffectPick -> state.copy(transition = state.transition.copy(effect = event.effect))
            is GroupEditorEvent.OnTransitionDurationPick ->
                state.copy(transition = state.transition.copy(durationMs = SlideTransition.duration(event.durationMs)))
            is GroupEditorEvent.OnPreviewSlidesLoaded ->
                if (state.step == GroupEditorStep.TRANSITION) state.copy(previewSlides = event.slides) else state
            is GroupEditorEvent.OnWallpaperClick -> state.copy(
                picked = if (event.id in state.picked) state.picked - event.id else state.picked + event.id,
            )
            GroupEditorEvent.OnPickConfirm -> state.confirmPick()
            GroupEditorEvent.OnPickCancel -> state.copy(step = GroupEditorStep.ARRANGE, stepGoesBack = true, picked = emptySet())
            GroupEditorEvent.OnAddClick -> state.copy(step = GroupEditorStep.PICK, stepGoesBack = false, picked = state.members.toSet())
            is GroupEditorEvent.OnMemberRemove -> state.copy(members = state.members - event.id)
            is GroupEditorEvent.OnMembersReorder -> state.copy(members = event.memberIds)
            is GroupEditorEvent.OnDurationPick -> state.copy(slideDuration = event.duration)
            GroupEditorEvent.OnSaveClick -> if (state.canSave) {
                state.copy(save = GroupSaveRequest(state.memberItems.map { it.id }, state.slideDuration, state.transition))
            } else {
                state
            }
            GroupEditorEvent.OnSaveFailed -> state.copy(save = null)
            GroupEditorEvent.OnCloseClick,
            is GroupEditorEvent.OnMemberEditClick,
            is GroupEditorEvent.OnMemberSettingsClick -> state
        }

    /**
     * Members keep their order; the newly picked ones follow, in the list's order. Picked to add to members, it
     * goes back to them; a new group's first pick goes on to its members.
     */
    private fun GroupEditorState.confirmPick(): GroupEditorState {
        if (!canConfirmPick) return this
        val kept = members.filter { it in picked }
        val added = pickable.map { it.id }.filter { it in picked && it !in kept }
        return copy(
            step = GroupEditorStep.ARRANGE,
            stepGoesBack = members.isNotEmpty(),
            members = kept + added,
            picked = emptySet(),
        )
    }
}
