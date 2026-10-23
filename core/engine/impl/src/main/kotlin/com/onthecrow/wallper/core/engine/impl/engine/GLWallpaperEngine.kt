package com.onthecrow.wallper.core.engine.impl.engine

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.gl.VideoRenderParams
import com.onthecrow.wallper.core.gl.VideoWallpaperRenderer
import com.onthecrow.wallper.core.gl.WallpaperGLThread
import com.onthecrow.wallper.core.gl.guard.GlRestartPolicy
import com.onthecrow.wallper.core.player.PlayerMedia
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import com.onthecrow.wallper.core.player.WallperPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays the original video and crops it on the GPU through UV coordinates.
 *
 * Only binds the two sides together: the renderer's video surface goes to the player. Each side recovers
 * on its own. The player replaces its decoder behind that surface; the GL side repairs EGL inside the GL
 * thread, and a broken or hung GL thread is recreated here without touching the player.
 */
internal class GLWallpaperEngine(
    surfaceHolder: () -> SurfaceHolder,
    wallpaper: WallpaperSpec.Media,
    playerFactory: WallperPlayerFactory,
    private val host: EngineHost,
) : WallpaperEngine() {

    private val holder = surfaceHolder()

    @Volatile
    private var surfaceWidth = holder.surfaceFrame.width()

    @Volatile
    private var surfaceHeight = holder.surfaceFrame.height()

    // размеры и поворот видео сохранены в БД при создании обоев
    private val renderParams = VideoRenderParams(surfaceWidth, surfaceHeight, wallpaper.crop, wallpaper.mediaInfo)

    private val player = playerFactory.create(
        PlayerMedia(wallpaper.sourcePath, wallpaper.clip.startMs, wallpaper.clip.endMs),
    )

    /** Frame callbacks and this class's coroutines, off the main thread. */
    private val callbackThread = HandlerThread("WallpaperFrames").apply { start() }
    private val callbackHandler = Handler(callbackThread.looper)
    private val scope = CoroutineScope(SupervisorJob() + callbackHandler.asCoroutineDispatcher("WallpaperFrames"))

    private val visible = MutableStateFlow(true)
    private val framesExpected = combine(visible, player.state) { isVisible, state ->
        isVisible && state == WallperPlayerState.Playing
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val glRestarts = GlRestartPolicy(SystemClock::elapsedRealtime)

    @Volatile
    private var gl = Gl()

    @Volatile
    private var released = false

    init {
        Timber.d("created")
        scope.launch {
            player.state.first { it is WallperPlayerState.Failed }
            host.showPlaceholder()
        }
        scope.launch { framesExpected.collect { gl.thread.setFramesExpected(it) } }
        scope.launch { watchGlHeartbeat() }
        player.play()
    }

    override fun pause() {
        Timber.d("paused")
        visible.value = false
        gl.thread.onPauseRendering()
        player.pause()
    }

    override fun resume() {
        Timber.d("resumed")
        visible.value = true
        gl.thread.onResumeRendering()
        gl.thread.requestRender()
        player.play()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        gl.thread.onSurfaceCreatedOrChanged(width, height)
    }

    override fun onSurfaceDestroyed() = gl.thread.onSurfaceDestroyed(waitMs = SURFACE_DESTROY_WAIT_MS)

    override fun release() {
        Timber.d("released")
        released = true
        val stopped = CountDownLatch(1)
        callbackHandler.post {
            scope.cancel()
            stopped.countDown()
        }
        stopped.await(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        player.release()
        gl.thread.requestExitAndWait()
        callbackThread.quitSafely()
    }

    /** A GL thread stuck inside a driver call can't notice that itself. */
    private suspend fun watchGlHeartbeat() {
        framesExpected.collectLatest { expected ->
            if (!expected) return@collectLatest
            var expectedSinceMs = SystemClock.elapsedRealtime()
            while (true) {
                delay(HEARTBEAT_CHECK_MS)
                val silentMs = SystemClock.elapsedRealtime() - maxOf(gl.thread.lastLoopAtMs, expectedSinceMs)
                if (silentMs > GL_HEARTBEAT_TIMEOUT_MS) {
                    restartGl("GL thread unresponsive for $silentMs ms")
                    expectedSinceMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** Callback thread. Replaces a broken or hung GL thread; the player keeps playing. */
    private fun restartGl(reason: String) {
        if (released) return
        Timber.w("Restarting GL: $reason")
        if (!glRestarts.tryAcquire()) {
            Timber.w("GL restart limit reached")
            host.showPlaceholder()
            return
        }
        // Waits until the decoder lets go of the old texture, which the old GL thread releases on exit.
        player.setVideoSurface(null)
        gl.thread.requestExitAndWait()
        gl = Gl().apply {
            if (!visible.value) thread.onPauseRendering()
            thread.setFramesExpected(framesExpected.value)
        }
    }

    /** One GL thread with its renderer. Recreated as a whole when GL can't be repaired in place. */
    private inner class Gl {
        val renderer = VideoWallpaperRenderer(
            renderParams = renderParams,
            callbackHandler = callbackHandler,
            listener = object : VideoWallpaperRenderer.Listener {
                override fun onFrameAvailable() = thread.requestRender()

                override fun onVideoSurfaceCreated(surface: Surface) {
                    if (gl === this@Gl && !released) player.setVideoSurface(surface)
                }
            },
        )

        val thread: WallpaperGLThread = WallpaperGLThread(
            surfaceHolder = holder,
            renderer = renderer,
            renderGuardListener = { stall ->
                callbackHandler.post { if (gl === this@Gl) restartGl("unrecoverable $stall") }
            },
        ).apply {
            start()
            onSurfaceCreatedOrChanged(surfaceWidth, surfaceHeight)
        }
    }

    private companion object {
        const val HEARTBEAT_CHECK_MS = 500L
        const val GL_HEARTBEAT_TIMEOUT_MS = 2_000L
        const val STOP_TIMEOUT_MS = 500L
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
