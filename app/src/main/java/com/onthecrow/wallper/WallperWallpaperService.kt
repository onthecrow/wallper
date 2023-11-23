@file:UnstableApi

package com.onthecrow.wallper

import android.app.WallpaperColors
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer


class WallperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return GLEngine()
    }

    @UnstableApi
    inner class GLEngine : Engine() {

        private val mediaPlayer: ExoPlayer = ExoPlayer.Builder(baseContext).build()

        override fun onComputeColors(): WallpaperColors? {
            // TODO implement color functionality
            return super.onComputeColors()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                mediaPlayer.play()
            } else {
                mediaPlayer.pause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            mediaPlayer.release()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            mediaPlayer.setVideoSurface(holder?.surface)
            mediaPlayer.setMediaItem(
                MediaItem.fromUri(
                    Uri.parse("android.resource://com.onthecrow.wallper/" + R.raw.video4)
                )
            )
            mediaPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            mediaPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            mediaPlayer.prepare()
            mediaPlayer.play()
        }
    }
}