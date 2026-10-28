package com.onthecrow.wallper.core.engine.impl.engine

import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperSettings
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.gl.RestartableGl
import com.onthecrow.wallper.core.gl.VideoRenderParams
import com.onthecrow.wallper.core.gl.VideoWallpaperRenderer
import com.onthecrow.wallper.core.gl.WallpaperGLThread
import com.onthecrow.wallper.core.player.PlayerMedia
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import com.onthecrow.wallper.core.player.WallperPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Plays the original video and crops it on the GPU through UV coordinates.
 *
 * Only binds the two sides together: the renderer's video surface goes to the player. Each side recovers
 * on its own. The player replaces its decoder behind that surface; the GL side repairs EGL inside the GL
 * thread, and a broken or hung GL thread is recreated here without touching the player. The crop pans
 * with the launcher's pages ([VideoWallpaperRenderer.pageOffset]); with the user's setting off the
 * service reports no pages.
 */
internal class GLWallpaperEngine(
    surfaceHolder: () -> SurfaceHolder,
    wallpaper: WallpaperSpec.Media,
    playerFactory: WallperPlayerFactory,
    settings: Flow<WallpaperSettings>,
    savingPower: Flow<Boolean>,
    screenOffs: Flow<Unit>,
    private val host: EngineHost,
) : WallpaperEngine() {

    private val holder = surfaceHolder()

    // The video size and rotation were stored with the wallpaper when it was created.
    private val renderParams = VideoRenderParams(
        holder.surfaceFrame.width(), holder.surfaceFrame.height(), wallpaper.crop, wallpaper.mediaInfo,
    )

    private val player = playerFactory.create(
        PlayerMedia(wallpaper.sourcePath, wallpaper.clip.startMs, wallpaper.clip.endMsOrNull),
    )

    /** Frame callbacks and this class's coroutines, off the main thread. */
    private val callbackThread = HandlerThread("WallpaperFrames").apply { start() }
    private val callbackHandler = Handler(callbackThread.looper)
    private val scope = CoroutineScope(SupervisorJob() + callbackHandler.asCoroutineDispatcher("WallpaperFrames"))

    private val visible = MutableStateFlow(true)
    private val framesExpected = combine(visible, player.state) { isVisible, state ->
        isVisible && state == WallperPlayerState.Playing
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val playback = VideoPlaybackControl(player, settings, savingPower, screenOffs, scope)

    /** The launcher's page offset, null without pages. */
    private val launcherOffset = MutableStateFlow<Float?>(null)

    /** What the renderer pans with; kept here for a renderer made after a GL restart. */
    @Volatile
    private var pageOffset: Float? = null

    // Waits until the decoder lets go of the old texture, which the old GL thread releases on exit.
    private val gl = RestartableGl(
        holder = holder,
        handler = callbackHandler,
        onGiveUp = host::showPlaceholder,
        beforeRestart = { player.setVideoSurface(null) },
        newSession = { Gl() },
    )

    @Volatile
    private var released = false

    init {
        Timber.d("created")
        gl.start()
        scope.launch {
            player.state.first { it is WallperPlayerState.Failed }
            host.showPlaceholder("The video player failed")
        }
        scope.launch { framesExpected.collect(gl::setFramesExpected) }
        scope.launch {
            launcherOffset.collect { offset ->
                pageOffset = offset
                gl.current.renderer.pageOffset = offset
                // A paused video gets no new frames to draw the move with.
                gl.current.thread.requestRender()
            }
        }
    }

    override fun pause() {
        Timber.d("paused")
        visible.value = false
        gl.setVisible(false)
        playback.setVisible(false)
    }

    override fun resume() {
        Timber.d("resumed")
        visible.value = true
        gl.setVisible(true)
        gl.current.thread.requestRender()
        playback.setVisible(true)
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        launcherOffset.value = xOffset.takeIf { xOffsetStep > 0f }
    }

    override fun onSurfaceChanged(width: Int, height: Int) = gl.onSurfaceChanged(width, height)

    override fun onSurfaceDestroyed() = gl.onSurfaceDestroyed(SURFACE_DESTROY_WAIT_MS)

    override fun release() {
        Timber.d("released")
        released = true
        callbackHandler.post { scope.cancel() }
        player.release()
        gl.release(STOP_TIMEOUT_MS)
        callbackThread.quitSafely()
    }

    /** One GL thread with its renderer. Recreated as a whole when GL can't be repaired in place. */
    private inner class Gl : RestartableGl.Session {
        val renderer = VideoWallpaperRenderer(
            renderParams = renderParams,
            callbackHandler = callbackHandler,
            listener = object : VideoWallpaperRenderer.Listener {
                override fun onFrameAvailable() = thread.requestRender()

                override fun onVideoSurfaceCreated(surface: Surface) {
                    if (gl.isCurrent(this@Gl) && !released) player.setVideoSurface(surface)
                }
            },
        ).apply { pageOffset = this@GLWallpaperEngine.pageOffset }

        override val thread: WallpaperGLThread = WallpaperGLThread(
            surfaceHolder = holder,
            renderer = renderer,
            renderGuardListener = { stall -> gl.restart(this@Gl, "unrecoverable $stall") },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 1_500L
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
