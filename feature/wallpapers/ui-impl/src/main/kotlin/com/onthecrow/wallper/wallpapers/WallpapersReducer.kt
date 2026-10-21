package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class WallpapersReducer @Inject constructor() : Reducer<WallpapersState, WallpapersEvent> {
    override suspend fun reduce(state: WallpapersState, event: WallpapersEvent): WallpapersState =
        when (event) {
            is WallpapersEvent.OnWallpapersChanged -> state.copy(items = event.items)
            is WallpapersEvent.OnWallpapersLoadFailed -> state.copy(error = event.message)
            else -> state
        }
}
