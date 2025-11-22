@file:UnstableApi

package com.onthecrow.wallper.service

import android.app.WallpaperColors
import android.graphics.BitmapFactory
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.UnstableApi
import com.onthecrow.wallper.R
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.domain.GetActiveWallpaperUseCase
import com.onthecrow.wallper.service.engine.WallpaperEngine
import com.onthecrow.wallper.service.engine.WallpaperEngineFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class WallperWallpaperService : WallpaperService() {

    @Inject
    lateinit var getActiveWallpaperUseCase: GetActiveWallpaperUseCase

    @Inject
    lateinit var wallpaperEngineFactory: WallpaperEngineFactory

    @Inject
    lateinit var watchhog: Watchhog

    override fun onCreateEngine(): Engine {
        Timber.d("onCreateEngine")
        return GLEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.d("onLowMemory")
    }

    @UnstableApi
    inner class GLEngine : Engine() {

        private val engineContext = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var wallpaperEngine: WallpaperEngine? = null
        private var computeColorsJob: Job? = null
        private var computedColors: WallpaperColors? = null

        private val _state = getActiveWallpaperUseCase()
            .onEach { wallpaperEntity ->
                Timber.d("New selected wallpaper: $wallpaperEntity")
                createEngine(wallpaperEntity)
            }
            .stateIn(engineContext, SharingStarted.Eagerly, null)

        override fun onComputeColors(): WallpaperColors? {
            return computedColors
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                wallpaperEngine?.resume()
            } else {
                wallpaperEngine?.pause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            Timber.d("onDestroy()")
            wallpaperEngine?.release()
            wallpaperEngine = null
            engineContext.cancel()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Timber.d("onSurfaceDestroyed()")
            wallpaperEngine?.pause()
        }

        private var surfaceHolder: SurfaceHolder? = null

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            Timber.d("onSurfaceCreated()")
            surfaceHolder = holder
            createEngine()
        }

        private fun createEngine(
            entity: WallpaperEntity? = _state.value,
            surfaceHolder: SurfaceHolder? = this.surfaceHolder
        ) {
            Timber.d("createEngine()")
            if (surfaceHolder == null) return
            wallpaperEngine?.release()
            wallpaperEngine = wallpaperEngineFactory.create(baseContext, { surfaceHolder }, entity)
            computeColors()
        }

        @Suppress("unused")
        private fun computeColors() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return

            computeColorsJob?.cancel()
            computeColorsJob = engineContext.launch(Dispatchers.IO) {
                val activeWallpaper = _state.value

                val bitmap = if (activeWallpaper == null) {
                    applicationContext.resources
                        .getDrawable(R.drawable.bg_engine_empty, null)
                        .toBitmap(
                            surfaceHolder?.surfaceFrame?.width() ?: 0,
                            surfaceHolder?.surfaceFrame?.height() ?: 0,
                        )
                } else {
                    BitmapFactory.decodeFile(activeWallpaper.thumbnailUri)
                }

                computedColors = bitmap?.let { WallpaperColors.fromBitmap(bitmap) }
                notifyColorsChanged()
            }
        }
    }
}
