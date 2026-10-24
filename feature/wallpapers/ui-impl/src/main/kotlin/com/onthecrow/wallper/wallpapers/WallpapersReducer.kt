package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.Reducer
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import javax.inject.Inject

internal class WallpapersReducer @Inject constructor() : Reducer<WallpapersState, WallpapersEvent> {
    override suspend fun reduce(state: WallpapersState, event: WallpapersEvent): WallpapersState =
        when (event) {
            is WallpapersEvent.OnWallpapersChanged -> state.copy(items = event.items.withPreviews(state.builtInPreviews))
            is WallpapersEvent.OnWallpapersLoadFailed -> state.copy(error = event.message)
            is WallpapersEvent.OnDeleteClick -> state.copy(deleteCandidate = event.id)
            is WallpapersEvent.OnDeleteConfirm,
            WallpapersEvent.OnDeleteDismiss -> state.copy(deleteCandidate = null)
            is WallpapersEvent.OnBuiltInPreviewLoaded -> {
                val previews = state.builtInPreviews + (event.wallpaper to event.path)
                state.copy(items = state.items.withPreviews(previews), builtInPreviews = previews)
            }
            else -> state
        }

    /** A built-in's picture is its preview, once it is rendered. */
    private fun List<WallpaperItem>.withPreviews(previews: Map<BuiltInWallpaper, String>) = map { item ->
        item.builtIn?.let(previews::get)?.let { item.copy(picturePath = it) } ?: item
    }
}
