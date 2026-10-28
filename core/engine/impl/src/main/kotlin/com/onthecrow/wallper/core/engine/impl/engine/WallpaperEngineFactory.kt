package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperSettingsSource
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.engine.impl.BuiltInWallpapers
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import javax.inject.Inject

internal class WallpaperEngineFactory @Inject constructor(
    private val playerFactory: WallperPlayerFactory,
    private val builtIns: BuiltInWallpapers,
    private val wallpaperSettings: WallpaperSettingsSource,
    private val powerSaving: PowerSaving,
    private val screenOffs: ScreenOffs,
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
        is WallpaperSpec.BuiltIn -> builtIns[wallpaper.wallpaper]
            ?.createEngine(baseContext, surfaceHolder, isPreview, host)
            ?: PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        is WallpaperSpec.ProcessedVideo -> VideoWallpaperEngine(
            surfaceHolder = surfaceHolder,
            processedPath = wallpaper.path,
            playerFactory = playerFactory,
            settings = wallpaperSettings.settings,
            savingPower = powerSaving.isSaving,
            screenOffs = screenOffs.events,
            host = host,
        )
        is WallpaperSpec.Media -> when (wallpaper.type) {
            MediaType.VIDEO -> GLWallpaperEngine(
                surfaceHolder = surfaceHolder,
                wallpaper = wallpaper,
                playerFactory = playerFactory,
                settings = wallpaperSettings.settings,
                savingPower = powerSaving.isSaving,
                screenOffs = screenOffs.events,
                host = host,
            )
            // TODO Images are not rendered yet and fall back to the placeholder.
            MediaType.IMAGE -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        }
    }
}
