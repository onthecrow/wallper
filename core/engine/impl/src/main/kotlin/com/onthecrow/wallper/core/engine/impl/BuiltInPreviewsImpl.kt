package com.onthecrow.wallper.core.engine.impl

import com.onthecrow.wallper.core.engine.BuiltInPreviews
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

internal class BuiltInPreviewsImpl @Inject constructor(
    private val builtIns: BuiltInWallpapers,
) : BuiltInPreviews {

    override fun previews(wallpaper: BuiltInWallpaper): Flow<String> = builtIns[wallpaper]?.previews() ?: emptyFlow()
}
