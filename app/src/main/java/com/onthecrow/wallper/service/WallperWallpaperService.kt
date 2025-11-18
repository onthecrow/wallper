@file:UnstableApi

package com.onthecrow.wallper.service

import android.app.ActivityManager
import android.app.WallpaperColors
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.onthecrow.wallper.R
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.domain.GetActiveWallpaperUseCase
import com.onthecrow.wallper.service.renderer.GLES20WallpaperRenderer
import com.onthecrow.wallper.service.renderer.GLWallpaperRenderer
import com.onthecrow.wallper.service.renderer.RendererParams
import com.onthecrow.wallper.util.MetadataUtils.getVideoMetadata
import com.onthecrow.wallper.util.croppedBitmap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig


@AndroidEntryPoint
class WallperWallpaperService : WallpaperService() {

    @Inject
    lateinit var getActiveWallpaperUseCase: GetActiveWallpaperUseCase

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

        private var glSurfaceView: GLWallpaperSurfaceView? = null
        private var renderer: GLWallpaperRenderer? = null
        private var mediaPlayer: ExoPlayer? = null
        private var computeColorsJob: Job? = null
        private var computedColors: WallpaperColors? = null

        private val _state = getActiveWallpaperUseCase()
            .onEach { wallpaperEntity ->
                Timber.d("New selected wallpaper: $wallpaperEntity")
                recreatePlayer(wallpaperEntity)
            }
            .stateIn(engineContext, SharingStarted.Eagerly, null)

        override fun onComputeColors(): WallpaperColors? {
            return computedColors
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(
                xOffset,
                yOffset,
                xOffsetStep,
                yOffsetStep,
                xPixelOffset,
                yPixelOffset
            )
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            Timber.d("${System.identityHashCode(this)} onSurfaceChanged()")
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Timber.d("${System.identityHashCode(this)} onVisibilityChanged($visible) ${mediaPlayer?.isPlaying} ${glSurfaceView}")
            if (visible) {
                glSurfaceView?.onResume()
                mediaPlayer?.play()
            } else {
                mediaPlayer?.pause()
                glSurfaceView?.onPause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            Timber.d("onDestroy()")
            engineContext.cancel()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Timber.d("onSurfaceDestroyed()")

            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            holder?.surface?.release()
            glSurfaceView?.apply {
                onPause()
                onDestroy()
                glSurfaceView = null
            }
            renderer?.dispose()
            renderer = null
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            Timber.d("onSurfaceCreated()")
//            val wallpaper = runBlocking {
//                getActiveWallpaperUseCase().firstOrNull()
//            }
            createGLSurfaceView(
                holder!!.surfaceFrame.width(),
                holder.surfaceFrame.height(),
            )
            recreatePlayer()
        }

        private fun recreatePlayer(entity: WallpaperEntity? = _state.value) {
            Timber.d("recreatePlayer()")
            mediaPlayer?.run {
                stop()
                release()
                mediaPlayer = null
            }
            // TODO refactor to factory or smth
            when {
                entity?.isVideo == true -> {
                    Timber.d("isVideo")

                    // 64КБ сегменты, тримим при reset’e
                    val allocator = DefaultAllocator(/* trimOnReset = */ true,
                        C.DEFAULT_BUFFER_SEGMENT_SIZE
                    )

                    val loadControl = DefaultLoadControl.Builder()
                        // 1.5–5 c буфера обычно более чем достаточно для обоев
                        .setBufferDurationsMs(
                            /* minBufferMs = */ 1500,
                            /* maxBufferMs = */ 5000,
                            /* bufferForPlaybackMs = */ 250,
                            /* bufferForPlaybackAfterRebufferMs = */ 500
                        )
                        // Приоритет «времени» над размером: не раздувать буфер сверх нужного времени
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .setAllocator(allocator)
                        // Если у вашей версии есть этот метод — можно дополнительно «потолок» в байтах:
                        // .setTargetBufferBytes(16 * 1024 * 1024)
                        .build()

                    mediaPlayer = ExoPlayer.Builder(baseContext)
                        .setLoadControl(loadControl)
                        .build()
                        .apply {
                            val dataSourceFactory = DefaultDataSource.Factory(baseContext)

                            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(entity.originalUri.toUri()))

                            watchhog.attachPlayer(this)

                            setMediaSource(mediaSource)

                            // This must be set after getting video info.
                            engineContext.launch(Dispatchers.IO) {
                                val videoMetadata =
                                    getVideoMetadata(baseContext, entity.originalUri)
                                withContext(Dispatchers.Main) {
                                    Timber.d(
                                        "Surface identity: ${
                                            System.identityHashCode(
                                                surfaceHolder.surface
                                            )
                                        }"
                                    )
                                    if (mediaPlayer == null) return@withContext
                                    renderer?.rendererParams = RendererParams.VideoParams(
                                        surfaceHolder.surfaceFrame.width(),
                                        surfaceHolder.surfaceFrame.height(),
                                        mediaPlayer!!,
                                        entity.shownRect,
                                        // TODO Bake video metadata in db on wallpaper creation (performance impact ~50ms)
                                        videoMetadata,
                                    )
                                }
                            }
                            repeatMode = ExoPlayer.REPEAT_MODE_ONE
                            volume = 0f
                            prepare()
                            play()
                        }
                }

                entity == null -> {
                    renderer?.rendererParams = RendererParams.PlaceholderParams(
                        surfaceHolder.surfaceFrame.width(),
                        surfaceHolder.surfaceFrame.height(),
                    )
                }

                else -> {
                    renderer?.rendererParams = RendererParams.PictureParams(
                        surfaceHolder.surfaceFrame.width(),
                        surfaceHolder.surfaceFrame.height(),
                        croppedBitmap(
                            surfaceHolder.surfaceFrame.width(),
                            surfaceHolder.surfaceFrame.height(),
                            entity.originalUri,
                            entity.shownRect
                        )
                    )
                }
            }
            glSurfaceView?.onResume()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                computeColors()
            }
            Timber.d("Player recreated with params: ${renderer?.rendererParams}")
        }

        private fun computeColors() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return

            computeColorsJob?.cancel()
            computeColorsJob = engineContext.launch(Dispatchers.IO) {
                val activeWallpaper = getActiveWallpaperUseCase().firstOrNull()

                val bitmap = if (activeWallpaper == null) {
                    applicationContext.resources
                        .getDrawable(R.drawable.bg_engine_empty, null)
                        .toBitmap(
                            surfaceHolder.surfaceFrame.width(),
                            surfaceHolder.surfaceFrame.height()
                        )
                } else {
                    BitmapFactory.decodeFile(activeWallpaper.thumbnailUri)
                }

                computedColors = bitmap?.let { WallpaperColors.fromBitmap(bitmap) }
                notifyColorsChanged()
            }
        }

        private fun createGLSurfaceView(width: Int, height: Int) {
            Timber.d("createGLSurfaceView()")
            glSurfaceView?.apply {
                onDestroy()
                glSurfaceView = null
            }
            glSurfaceView = GLWallpaperSurfaceView(baseContext).apply {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val configInfo = activityManager.deviceConfigurationInfo
                /*if (configInfo.reqGlEsVersion >= 0x30000) {
                    Utils.debug(
                        xyz.alynx.livewallpaper.GLWallpaperService.GLWallpaperEngine.TAG,
                        "Support GLESv3"
                    )
                    glSurfaceView.setEGLContextClientVersion(3)
                    renderer = GLES30WallpaperRenderer(context)
                } else */
                if (configInfo.reqGlEsVersion >= 0x20000) {
                    Timber.d("GLESv2 is supported")
                    setEGLContextClientVersion(2)
                    renderer = GLES20WallpaperRenderer(context, { this }, width, height)
                } else {
//                    Toast.makeText(context, R.string.gles_version, Toast.LENGTH_LONG).show()
                    throw RuntimeException("Needs GLESv2 or higher")
                }
                preserveEGLContextOnPause = true
                // todo this line produces awful visuals on some devices
//                setEGLConfigChooser(5, 6, 5, 0, 0, 0)
                holder.setFormat(PixelFormat.RGB_565)
                setRenderer(renderer)
                // On demand render will lead to black screen.
                // TODO change to RENDERMODE_WHEN_DIRTY and add manual fps
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        }

        inner class GLWallpaperSurfaceView(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder {
                return surfaceHolder
            }

            fun onDestroy() {
                super.onDetachedFromWindow()
                Timber.d("onDestroy()")
            }
        }
    }
}