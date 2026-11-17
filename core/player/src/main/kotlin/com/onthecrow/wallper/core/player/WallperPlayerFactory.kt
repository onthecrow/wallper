package com.onthecrow.wallper.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WallperPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @OptIn(UnstableApi::class)
    /**
     * @param keepsStandby false for a player with a single decoder: it makes its spare one only when a
     * stall needs it, so it recovers later by as long as a decoder takes to start.
     */
    fun create(media: PlayerMedia, keepsStandby: Boolean = true): WallperPlayer =
        DefaultWallperPlayer(context, media, keepsStandby)
}
