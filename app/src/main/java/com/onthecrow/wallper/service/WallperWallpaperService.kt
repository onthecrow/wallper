@file:UnstableApi

package com.onthecrow.wallper.service

import android.app.ActivityManager
import android.app.WallpaperColors
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.R
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.domain.GetActiveWallpaperUseCase
import com.onthecrow.wallper.service.renderer.GLES20WallpaperRenderer
import com.onthecrow.wallper.service.renderer.GLWallpaperRenderer
import com.onthecrow.wallper.service.renderer.RendererParams
import com.onthecrow.wallper.util.croppedBitmap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class WallperWallpaperService : WallpaperService() {

    @Inject
    lateinit var getActiveWallpaperUseCase: GetActiveWallpaperUseCase

    override fun onCreateEngine(): Engine {
        return GLEngine(getActiveWallpaperUseCase)
    }

    @UnstableApi
    inner class GLEngine(getActiveWallpaperUseCase: GetActiveWallpaperUseCase) : Engine() {

        private var glSurfaceView: GLEngine.GLWallpaperSurfaceView? = null
        private var renderer: GLWallpaperRenderer? = null
        private var mediaPlayer: ExoPlayer? = null
        private var updateJob: Job?
        private var surfaceCreated = false

        init {
            updateJob = getActiveWallpaperUseCase()
                .onEach {
                    Timber.d("New selected wallpaper")
                    recreatePlayer(it)
                }
                .launchIn(MainScope())
        }

        override fun onComputeColors(): WallpaperColors? {
            val activeWallpaper = runBlocking { getActiveWallpaperUseCase().firstOrNull() }
            val bitmap = if (activeWallpaper == null) {
                applicationContext.resources
                    .getDrawable(R.drawable.bg_engine_empty, null)
                    .toBitmap(surfaceHolder.surfaceFrame.width(), surfaceHolder.surfaceFrame.height())
            } else {
                BitmapFactory.decodeFile(activeWallpaper.thumbnailUri)
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                bitmap?.let { WallpaperColors.fromBitmap(bitmap) }
            } else {
                null
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                mediaPlayer?.play()
                glSurfaceView?.onResume()
            } else {
                mediaPlayer?.pause()
                glSurfaceView?.onPause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            updateJob?.cancel()
            updateJob = null
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
            val wallpaper = runBlocking {
                getActiveWallpaperUseCase().firstOrNull()
            }
            createGLSurfaceView(
                holder!!.surfaceFrame.width(),
                holder.surfaceFrame.height(),
            )
            recreatePlayer(wallpaper)
            surfaceCreated = true
        }

        private fun recreatePlayer(wallpaperEntity: WallpaperEntity?) {
            Timber.d("recreatePlayer()")
            glSurfaceView?.onPause()
            mediaPlayer?.run { release() }
            // TODO refactor to factory or smth
            when {
                wallpaperEntity?.isVideo == true -> {
                    mediaPlayer = ExoPlayer.Builder(baseContext).build().apply {
                        setMediaItem(
                            MediaItem.fromUri(
                                Uri.parse(wallpaperEntity.originalUri)
                            )
                        )
                        // This must be set after getting video info.
                        Timber.d("Surface identity: ${System.identityHashCode(surfaceHolder.surface)}")
                        renderer?.rendererParams = RendererParams.VideoParams(
                            surfaceHolder.surfaceFrame.width(),
                            surfaceHolder.surfaceFrame.height(),
                            this,
                            wallpaperEntity.shownRect,
                            getVideoMetadata(wallpaperEntity.originalUri),
                        )
                        repeatMode = ExoPlayer.REPEAT_MODE_ONE
                        volume = 0f
                        prepare()
                        play()
                    }
                }
                wallpaperEntity == null -> {
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
                            wallpaperEntity.originalUri,
                            wallpaperEntity.shownRect
                        )
                    )
                }
            }
            glSurfaceView?.onResume()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                notifyColorsChanged()
            }
            Timber.d("Player recreated with params: ${renderer?.rendererParams}")
        }

        private fun getVideoMetadata(path: String): VideoMetadata {
            val mmr = MediaMetadataRetriever()
//            mmr.setDataSource(path)
            mmr.setDataSource(baseContext, Uri.parse(Uri.encode(path)))
            val rotation = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )
            val width = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )
            val height = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )
            mmr.release()
            return VideoMetadata(
                rotation?.toInt() ?: 0,
                width?.toInt() ?: 0,
                height?.toInt() ?: 0,
            )
        }

        private fun createGLSurfaceView(width: Int, height: Int) {
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
                    renderer = GLES20WallpaperRenderer(context, width, height)
                } else {
//                    Toast.makeText(context, R.string.gles_version, Toast.LENGTH_LONG).show()
//                    throw RuntimeException("Needs GLESv2 or higher")
                }
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                // On demand render will lead to black screen.
                // TODO change to RENDERMODE_WHEN_DIRTY and add manual fps
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }

        inner class GLWallpaperSurfaceView(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder {
                return surfaceHolder
            }

            fun onDestroy() {
                super.onDetachedFromWindow()
            }
        }
    }
}