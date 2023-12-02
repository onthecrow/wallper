@file:UnstableApi

package com.onthecrow.wallper.service

import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.domain.GetActiveWallpaperUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@AndroidEntryPoint
class WallperWallpaperService : WallpaperService() {

    @Inject
    lateinit var getActiveWallpaperUseCase: GetActiveWallpaperUseCase

    override fun onCreateEngine(): Engine {
        return GLEngine()
    }

    @UnstableApi
    inner class GLEngine : Engine() {

        private var mediaPlayer: ExoPlayer? = null
        // TODO inject scope
        private val watcher = getActiveWallpaperUseCase()
            .onEach { recreatePlayer(it) }
            .stateIn(MainScope(), SharingStarted.Eagerly, null)

        private fun recreatePlayer(wallpaperEntity: WallpaperEntity) {
            mediaPlayer?.run { release() }
            mediaPlayer = ExoPlayer.Builder(baseContext).build().apply {
                setMediaItem(
                    MediaItem.fromUri(
                        Uri.parse(wallpaperEntity.originalUri)
                    )
                )
                setVideoSurface(surfaceHolder.surface)
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                prepare()
                play()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                mediaPlayer?.play()
            } else {
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            mediaPlayer?.release()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            watcher.value?.let { recreatePlayer(it) }
        }
    }
}