package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.mvi.Reducer
import com.onthecrow.wallper.wallpapers.model.withPreviews
import javax.inject.Inject

internal class WallpapersReducer @Inject constructor() : Reducer<WallpapersState, WallpapersEvent> {
    override suspend fun reduce(state: WallpapersState, event: WallpapersEvent): WallpapersState =
        when (event) {
            is WallpapersEvent.OnWallpapersChanged ->
                state.copy(items = event.items.withPreviews(state.builtInPreviews, state.seriesPictures))
            is WallpapersEvent.OnWallpapersLoadFailed -> state.copy(error = event.message)
            is WallpapersEvent.OnDeleteClick -> state.copy(deleteCandidate = event.id)
            is WallpapersEvent.OnDeleteConfirm,
            WallpapersEvent.OnDeleteDismiss -> state.copy(deleteCandidate = null)
            WallpapersEvent.OnAddClick -> state.copy(isAddDialogShown = true)
            WallpapersEvent.OnAddDismiss,
            WallpapersEvent.OnAddMediaClick,
            WallpapersEvent.OnAddSeriesClick -> state.copy(isAddDialogShown = false)
            is WallpapersEvent.OnBuiltInPreviewLoaded -> {
                val previews = state.builtInPreviews + (event.wallpaper to event.path)
                state.copy(items = state.items.withPreviews(previews, state.seriesPictures), builtInPreviews = previews)
            }
            is WallpapersEvent.OnSeriesPictureMade -> {
                val pictures = state.seriesPictures + (event.groupId to event.path)
                state.copy(items = state.items.withPreviews(state.builtInPreviews, pictures), seriesPictures = pictures)
            }
            else -> state
        }
}
