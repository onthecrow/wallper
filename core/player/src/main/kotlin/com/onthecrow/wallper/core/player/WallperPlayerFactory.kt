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
    fun create(media: PlayerMedia): WallperPlayer = DefaultWallperPlayer(context, media)
}
