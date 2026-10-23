package com.onthecrow.wallper.core.engine.impl

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import com.onthecrow.wallper.core.engine.ActiveWallpaperSource
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.engine.impl.engine.EngineHost
import com.onthecrow.wallper.core.engine.impl.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.impl.engine.WallpaperEngineFactory
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class WallperWallpaperService : WallpaperService() {

    @Inject
    internal lateinit var activeWallpaperSource: ActiveWallpaperSource

    @Inject
    internal lateinit var wallpaperEngineFactory: WallpaperEngineFactory

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

    private companion object {
        const val PLACEHOLDER_RETRY_MS = 5 * 60_000L

        /** The width colors are computed at: WallpaperColors scales down anyway. */
        const val COLORS_SIZE = 112
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun colorsOf(bitmap: Bitmap): WallpaperColors = WallpaperColors.fromBitmap(bitmap).also { bitmap.recycle() }

    /** The thumbnail decoded at about [COLORS_SIZE] wide, never at full size. */
    private fun decodeSmall(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COLORS_SIZE) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun BuiltInWallpaper.colors(): WallpaperColors = when (this) {
        BuiltInWallpaper.BLACK_HOLE -> blackHoleColors()
    }

    /** The black hole: almost black with a warm amber accent, dark theme friendly (§11.4). */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun blackHoleColors(): WallpaperColors {
        val primary = Color.valueOf(0.03f, 0.025f, 0.02f)
        val secondary = Color.valueOf(0.85f, 0.55f, 0.2f)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WallpaperColors(primary, secondary, null, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
        } else {
            WallpaperColors(primary, secondary, null)
        }
    }

    inner class GLEngine : Engine(), EngineHost {

        private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val mainHandler = Handler(Looper.getMainLooper())
        private var destroyed = false

        /** The wallpaper the current engine was created for. */
        private var currentWallpaper: WallpaperSpec? = null

        /**
         * The wallpaper whose recovery budget ran out: shown as the placeholder until the user picks a
         * wallpaper (this one again too), or it is visible again after [PLACEHOLDER_RETRY_MS].
         */
        private var failedWallpaper: WallpaperSpec? = null
        private var failedAtMs = 0L

        /** The user picked a wallpaper: the database emission that follows may retry the failed one. */
        private var retryRequested = false

        /** The database has answered once: until then there is no engine, rather than a flash of the placeholder. */
        private var loaded = false
        private var wallpaperEngine: WallpaperEngine? = null
        private var computeColorsJob: Job? = null
        private var computedColors: WallpaperColors? = null
        private var surfaceHolder: SurfaceHolder? = null

        private val activeWallpaper = activeWallpaperSource.activeWallpaperFlow
            .catch { error ->
                Timber.e(error, "No active wallpaper from the database")
                emit(null)
            }
            .onEach { wallpaper ->
                if (!loaded) {
                    loaded = true
                    createEngine(wallpaper)
                    return@onEach
                }
                // The database re-emits on any table change. A new engine is for a different wallpaper,
                // or for the failed one when the user picked it again.
                val retry = retryRequested && failedWallpaper != null && wallpaper == failedWallpaper
                retryRequested = false
                if (wallpaper == currentWallpaper && !retry) return@onEach
                Timber.d("New selected wallpaper: $wallpaper, retry=$retry")
                if (retry) failedWallpaper = null
                createEngine(wallpaper)
            }
            .stateIn(engineScope, SharingStarted.Eagerly, null)

        init {
            activeWallpaperSource.activations
                .onEach { retryRequested = true }
                .launchIn(engineScope)
        }

        override fun onComputeColors(): WallpaperColors? {
            return computedColors
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            // Whatever broke it (heat, a driver hiccup) may have passed by now.
            if (visible && failedWallpaper != null && SystemClock.elapsedRealtime() - failedAtMs > PLACEHOLDER_RETRY_MS) {
                Timber.i("Retrying $failedWallpaper")
                failedWallpaper = null
                createEngine()
                return
            }
            if (visible) {
                wallpaperEngine?.resume()
            } else {
                wallpaperEngine?.pause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            Timber.d("onDestroy()")
            destroyed = true
            mainHandler.removeCallbacksAndMessages(null)
            wallpaperEngine?.release()
            wallpaperEngine = null
            engineScope.cancel()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Timber.d("onSurfaceDestroyed()")
            wallpaperEngine?.pause()
            wallpaperEngine?.onSurfaceDestroyed()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            wallpaperEngine?.onSurfaceChanged(width, height)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            wallpaperEngine?.onOffsetsChanged(xOffset, xOffsetStep)
        }

        // API 30+: the system zooms the wallpaper out while unlocking, opening the shade or an app.
        override fun onZoomChanged(zoom: Float) {
            super.onZoomChanged(zoom)
            wallpaperEngine?.onZoomChanged(zoom)
        }

        // EngineHost: called from the engines' threads.

        override fun showPlaceholder(reason: String, error: Throwable?) {
            mainHandler.post {
                if (destroyed) return@post
                // An error: the reporting tree turns it into a crash report with the GPU and the reason.
                Timber.e(error, "Placeholder instead of %s: %s", currentWallpaper, reason)
                failedWallpaper = currentWallpaper
                failedAtMs = SystemClock.elapsedRealtime()
                createEngine()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            Timber.d("onSurfaceCreated()")
            surfaceHolder = holder
            createEngine()
        }

        private fun createEngine(
            wallpaper: WallpaperSpec? = activeWallpaper.value,
            surfaceHolder: SurfaceHolder? = this.surfaceHolder
        ) {
            Timber.d("createEngine()")
            if (surfaceHolder == null || !loaded) return
            wallpaperEngine?.release()
            if (wallpaper != currentWallpaper) {
                currentWallpaper = wallpaper
                failedWallpaper = null
            }
            val shownWallpaper = wallpaper?.takeIf { it != failedWallpaper }
            wallpaperEngine = wallpaperEngineFactory
                .create(baseContext, { surfaceHolder }, shownWallpaper, isPreview, this)
                .also { if (!isVisible) it.pause() }
            computeColors(shownWallpaper)
        }

        /** From the wallpaper on screen: the placeholder's when it failed. Small bitmaps are enough for colors. */
        private fun computeColors(wallpaper: WallpaperSpec?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return

            computeColorsJob?.cancel()
            computeColorsJob = engineScope.launch(Dispatchers.IO) {
                computedColors = when (wallpaper) {
                    is WallpaperSpec.BuiltIn -> wallpaper.wallpaper.colors()
                    is WallpaperSpec.Media -> decodeSmall(wallpaper.thumbnailPath)?.let(::colorsOf)
                    null -> {
                        val frame = surfaceHolder?.surfaceFrame
                        val height = if (frame == null || frame.width() <= 0) COLORS_SIZE else COLORS_SIZE * frame.height() / frame.width()
                        applicationContext.resources.getDrawable(R.drawable.bg_engine_empty, null)
                            .toBitmap(COLORS_SIZE, height.coerceAtLeast(1))
                            .let(::colorsOf)
                    }
                }
                notifyColorsChanged()
            }
        }
    }
}
