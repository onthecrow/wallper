package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettingsStore
import com.onthecrow.wallper.core.blackhole.renderer.LensTablesRepository
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import javax.inject.Inject

internal class WallpaperEngineFactory @Inject constructor(
    private val playerFactory: WallperPlayerFactory,
    private val blackHoleSettings: BlackHoleSettingsStore,
    private val lensTables: LensTablesRepository,
) {
    /**
     * @param wallpaper the wallpaper to show, or null for the placeholder.
     * @param isPreview the system picker's preview: built-ins render cheaper there.
     */
    fun create(
        baseContext: Context,
        surfaceHolder: () -> SurfaceHolder,
        wallpaper: WallpaperSpec?,
        isPreview: Boolean,
        host: EngineHost,
    ): WallpaperEngine = when (wallpaper) {
        null -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        is WallpaperSpec.BuiltIn -> when (wallpaper.wallpaper) {
            BuiltInWallpaper.BLACK_HOLE ->
                BlackHoleWallpaperEngine(baseContext, surfaceHolder, isPreview, host, blackHoleSettings, lensTables)
        }
        is WallpaperSpec.Media -> {
            val processedPath = wallpaper.processedPath
            when {
                wallpaper.type == MediaType.VIDEO && processedPath != null ->
                    VideoWallpaperEngine(surfaceHolder, processedPath, playerFactory, host)
                wallpaper.type == MediaType.VIDEO ->
                    GLWallpaperEngine(surfaceHolder, wallpaper, playerFactory, host)
                // TODO Images are not rendered yet and fall back to the placeholder (ARCHITECTURE_MIGRATION.md, TODO).
                else -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
            }
        }
    }
}
