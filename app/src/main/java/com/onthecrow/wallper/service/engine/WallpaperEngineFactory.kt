package com.onthecrow.wallper.service.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.service.ExoPlayerFactory
import javax.inject.Inject

class WallpaperEngineFactory @Inject constructor(
    private val exoPlayerFactory: ExoPlayerFactory,
) {
    fun create(baseContext: Context, surfaceHolder: () -> SurfaceHolder, wallpaperEntity: WallpaperEntity): WallpaperEngine {
//        return GLWallpaperEngine(baseContext, exoPlayerFactory, surfaceHolder, wallpaperEntity)
        return when {
            wallpaperEntity.isVideo && wallpaperEntity.processedUri.isNotBlank() -> return VideoWallpaperEngine(baseContext, exoPlayerFactory, surfaceHolder, wallpaperEntity)
            wallpaperEntity.isVideo -> return GLWallpaperEngine(baseContext, exoPlayerFactory, surfaceHolder, wallpaperEntity)
            else -> StillWallpaperEngine()
        }
    }
}