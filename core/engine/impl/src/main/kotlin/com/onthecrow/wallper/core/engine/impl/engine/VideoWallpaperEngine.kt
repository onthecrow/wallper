package com.onthecrow.wallper.core.engine.impl.engine

import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperSettings
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

/**
 * Plays a video the editor has already cropped and trimmed, straight onto the wallpaper surface.
 *
 * With [onReady] it is a series' slide made off screen: hidden, on a single decoder that is prepared
 * without a surface, ready once its first frame is decoded. It gets the window at [show]; moving a decoder
 * there takes a few frames, and the window keeps the slide before it meanwhile.
 */
internal class VideoWallpaperEngine(
    surfaceHolder: () -> SurfaceHolder,
    processedPath: String,
    playerFactory: WallperPlayerFactory,
    settings: Flow<WallpaperSettings>,
    savingPower: Flow<Boolean>,
    screenOffs: Flow<Unit>,
    private val host: EngineHost,
    onReady: (() -> Unit)? = null,
) : WallpaperEngine() {

    private val holder = surfaceHolder()
    private val isSlide = onReady != null
    private val player = playerFactory.create(PlayerMedia(processedPath), keepsStandby = !isSlide)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playback = VideoPlaybackControl(player, settings, savingPower, screenOffs, scope, startsVisible = !isSlide)

    init {
        Timber.d("created")
        scope.launch {
            player.state.first { it is WallperPlayerState.Failed }
            host.showPlaceholder("The video player failed")
        }
        if (onReady == null) {
            player.setVideoSurface(holder.surface)
        } else {
            scope.launch { if (player.awaitPrepared(PREPARE_TIMEOUT_MS)) onReady() }
        }
    }

    override fun show() = player.setVideoSurface(holder.surface)

    override fun pause() = playback.setVisible(false)

    override fun resume() = playback.setVisible(true)

    // The system takes the surface back once onSurfaceDestroyed returns; setVideoSurface waits for the decoder.
    override fun onSurfaceDestroyed() = player.setVideoSurface(null)

    override fun release() {
        Timber.d("release")
        scope.cancel()
        player.release()
    }

    private companion object {
        /** Past this a slide isn't worth waiting for; it shows when its first frame comes. */
        const val PREPARE_TIMEOUT_MS = 10_000L
    }
}
