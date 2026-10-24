package com.onthecrow.wallper.core.player

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.util.UnstableApi
import com.onthecrow.wallper.core.player.engine.ExoEngineFactory
import com.onthecrow.wallper.core.player.supervisor.EngineSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owns the player thread: the supervisor's coroutines and every ExoPlayer live there. */
@UnstableApi
internal class DefaultWallperPlayer(
    context: Context,
    media: PlayerMedia,
) : WallperPlayer {

    private val thread = HandlerThread("WallperPlayer").apply { start() }
    private val handler = Handler(thread.looper)
    private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher("WallperPlayer"))
    private val playWhenReady = MutableStateFlow(false)

    private val supervisor = EngineSupervisor(
        factory = ExoEngineFactory(context, media, handler),
        clock = SystemClock::elapsedRealtime,
        log = { Timber.tag(TAG).d(it) },
    )

    override val state: StateFlow<WallperPlayerState> get() = supervisor.state

    init {
        scope.launch { supervisor.run(playWhenReady) }
    }

    override fun setVideoSurface(surface: Surface?) {
        onPlayerThread(SURFACE_TIMEOUT_MS) { supervisor.setSurface(surface) }
    }

    override fun play() {
        playWhenReady.value = true
    }

    override fun pause() {
        playWhenReady.value = false
    }

    // Posted, not awaited: they reach the supervisor before a play() that follows them, which goes through
    // the same handler.
    override fun setLooping(looping: Boolean) {
        handler.post { supervisor.setLooping(looping) }
    }

    override fun seekToStart() {
        handler.post { supervisor.seekToStart() }
    }

    override fun release() {
        onPlayerThread(RELEASE_TIMEOUT_MS) {
            scope.cancel()
            supervisor.releaseAll()
        }
        thread.quitSafely()
    }

    /** Runs [block] on the player thread and waits for it, at most [timeoutMs]. */
    private fun onPlayerThread(timeoutMs: Long, block: () -> Unit) {
        if (Looper.myLooper() == thread.looper) return block()
        val done = CountDownLatch(1)
        val posted = handler.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        if (posted && !done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            Timber.tag(TAG).w("Player thread didn't respond in $timeoutMs ms")
        }
    }

    private companion object {
        const val TAG = "WallperPlayer"
        const val RELEASE_TIMEOUT_MS = 1_000L

        /** ExoPlayer itself waits up to 2 s for the decoder to detach. */
        const val SURFACE_TIMEOUT_MS = 2_500L
    }
}
