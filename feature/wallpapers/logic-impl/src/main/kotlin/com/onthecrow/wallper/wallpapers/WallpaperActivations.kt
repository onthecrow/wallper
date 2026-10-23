package com.onthecrow.wallper.wallpapers

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Every pick of a wallpaper in the app, the active one too. The wallpaper service runs in this process. */
@Singleton
internal class WallpaperActivations @Inject constructor() {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val flow: Flow<Unit> = events.asSharedFlow()

    fun notifyActivated() {
        events.tryEmit(Unit)
    }
}
