package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import androidx.media3.common.util.UnstableApi
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.player.ExoPlayerFactory
import javax.inject.Inject

@UnstableApi
internal class WallpaperEngineFactory @Inject constructor(
    private val exoPlayerFactory: ExoPlayerFactory,
) {
    fun create(baseContext: Context, surfaceHolder: () -> SurfaceHolder, wallpaper: WallpaperSpec?): WallpaperEngine {
        val processedPath = wallpaper?.processedPath
        return when {
            wallpaper == null -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
            wallpaper.type == MediaType.VIDEO && processedPath != null ->
                VideoWallpaperEngine(baseContext, exoPlayerFactory, surfaceHolder, processedPath)
            wallpaper.type == MediaType.VIDEO ->
                GLWallpaperEngine(baseContext, exoPlayerFactory, surfaceHolder, wallpaper)
            // TODO Images are not rendered yet and fall back to the placeholder (ARCHITECTURE_MIGRATION.md, TODO).
            else -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        }
    }
}
