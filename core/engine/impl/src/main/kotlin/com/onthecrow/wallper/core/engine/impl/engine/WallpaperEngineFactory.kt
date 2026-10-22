package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import javax.inject.Inject

internal class WallpaperEngineFactory @Inject constructor(
    private val playerFactory: WallperPlayerFactory,
) {
    /** @param wallpaper the wallpaper to show, or null for the placeholder. */
    fun create(
        baseContext: Context,
        surfaceHolder: () -> SurfaceHolder,
        wallpaper: WallpaperSpec?,
        host: EngineHost,
    ): WallpaperEngine {
        val processedPath = wallpaper?.processedPath
        return when {
            wallpaper == null -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
            wallpaper.type == MediaType.VIDEO && processedPath != null ->
                VideoWallpaperEngine(surfaceHolder, processedPath, playerFactory, host)
            wallpaper.type == MediaType.VIDEO ->
                GLWallpaperEngine(surfaceHolder, wallpaper, playerFactory, host)
            // TODO Images are not rendered yet and fall back to the placeholder (ARCHITECTURE_MIGRATION.md, TODO).
            else -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        }
    }
}
