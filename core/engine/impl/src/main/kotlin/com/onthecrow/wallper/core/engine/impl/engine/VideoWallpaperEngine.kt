package com.onthecrow.wallper.core.engine.impl.engine

import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.VideoWallpaperSettings
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.player.PlayerMedia
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import com.onthecrow.wallper.core.player.WallperPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/** Plays a video the editor has already cropped and trimmed, straight onto the wallpaper surface. */
internal class VideoWallpaperEngine(
    surfaceHolder: () -> SurfaceHolder,
    processedPath: String,
    playerFactory: WallperPlayerFactory,
    settings: Flow<VideoWallpaperSettings>,
    savingPower: Flow<Boolean>,
    private val host: EngineHost,
) : WallpaperEngine() {

    private val player = playerFactory.create(PlayerMedia(processedPath))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playback = VideoPlaybackControl(player, settings, savingPower, scope)

    init {
        Timber.d("created")
        scope.launch {
            player.state.first { it is WallperPlayerState.Failed }
            host.showPlaceholder("The video player failed")
        }
        player.setVideoSurface(surfaceHolder().surface)
    }

    override fun pause() = playback.setVisible(false)

    override fun resume() = playback.setVisible(true)

    // The system takes the surface back once onSurfaceDestroyed returns; setVideoSurface waits for the decoder.
    override fun onSurfaceDestroyed() = player.setVideoSurface(null)

    override fun release() {
        Timber.d("release")
        scope.cancel()
        player.release()
    }
}
