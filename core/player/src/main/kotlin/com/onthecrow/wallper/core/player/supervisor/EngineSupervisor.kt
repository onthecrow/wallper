package com.onthecrow.wallper.core.player.supervisor

import android.view.Surface
import androidx.media3.common.PlaybackException
import com.onthecrow.wallper.core.player.FailureCause
import com.onthecrow.wallper.core.player.StallCause
import com.onthecrow.wallper.core.player.WallperPlayerState
import com.onthecrow.wallper.core.player.engine.EngineFactory
import com.onthecrow.wallper.core.player.engine.ManagedEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps two engines: the active one plays on the surface, the standby one is prepared off-screen and
 * paused. When [StallWatchdog] reports a stall, the supervisor either switches to the standby or gives
 * up with [WallperPlayerState.Failed]. The stalled engine is replaced by a new standby at the switch.
 * A clip that doesn't loop and reaches its end isn't a stall: it stays there, [WallperPlayerState.Ended].
 *
 * Runs on the player thread, as every ExoPlayer call must; nothing here is thread-safe.
 */
internal class EngineSupervisor(
    private val factory: EngineFactory,
    clock: () -> Long,
    private val log: (String) -> Unit = {},
    private val pausedReleaseMs: Long = PAUSED_RELEASE_MS,
    private val standbyReadyTimeoutMs: Long = STANDBY_READY_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow<WallperPlayerState>(WallperPlayerState.Idle)
    val state: StateFlow<WallperPlayerState> = _state.asStateFlow()

    private val watchdog = StallWatchdog(clock)
    private val retryPolicy = RetryPolicy(clock)

    private var surface: Surface? = null
    private var active: ManagedEngine? = null
    private var standby: ManagedEngine? = null
    private var resumePositionMs = 0L
    private var looping = true

    suspend fun run(playWhenReady: Flow<Boolean>) {
        createEngines()
        playWhenReady.distinctUntilChanged().collectLatest { play ->
            if (_state.value is WallperPlayerState.Failed) return@collectLatest
            if (play) play() else pause()
        }
    }

    /** Hands [surface] to the active engine and to every engine switched to later. */
    fun setSurface(surface: Surface?) {
        this.surface = surface
        active?.setSurface(surface)
    }

    /** For every engine, those created later too. */
    fun setLooping(looping: Boolean) {
        this.looping = looping
        active?.setLooping(looping)
        standby?.setLooping(looping)
    }

    /** The active engine jumps to the clip start; engines created later start there too. */
    fun seekToStart() {
        resumePositionMs = 0
        active?.seekTo(0)
    }

    fun releaseAll() {
        active?.release()
        active = null
        standby?.release()
        standby = null
    }

    private suspend fun play() {
        var engine = active ?: createEngines()
        // Played to the end before and looping now: start the clip over. Without looping it stays ended.
        if (engine.isEnded.value && looping) engine.seekTo(0)
        engine.play()
        _state.value = WallperPlayerState.Playing
        while (true) {
            val cause = watchOrEnd(engine)
            if (cause == null) {
                _state.value = WallperPlayerState.Ended
                return
            }
            log("Engine #${engine.id}: $cause")
            if (cause is StallCause.Failed && cause.errorCode in CONTENT_ERROR_CODES) {
                return fail(FailureCause.ContentBroken(cause.errorCode))
            }
            val next = awaitStandby(cause) ?: return fail(FailureCause.RetriesExhausted(cause))
            // The loop to the clip start never happened: start the next iteration instead.
            val positionMs = if (cause == StallCause.StuckAtClipEnd) 0 else engine.positionMs
            engine = switchTo(next, from = engine, positionMs)
            _state.value = WallperPlayerState.Playing
        }
    }

    /** Suspends while [engine] plays; returns why it stalled, or null once it ended without looping. */
    private suspend fun watchOrEnd(engine: ManagedEngine): StallCause? = merge<StallCause?>(
        engine.isEnded.filter { it }.map { null },
        flow { emit(watchdog.watch(engine)) },
    ).first()

    /** Paused for long: frees both decoders; the next [play] creates them again where playback stopped. */
    private suspend fun pause() {
        _state.value = WallperPlayerState.Idle
        active?.pause()
        delay(pausedReleaseMs)
        active?.let {
            log("Paused for $pausedReleaseMs ms, releasing engines")
            resumePositionMs = it.positionMs
        }
        releaseAll()
    }

    /** Returns the active engine. */
    private fun createEngines(): ManagedEngine {
        val engine = factory.create(resumePositionMs, surface, playWhenReady = false).apply { setLooping(looping) }
        active = engine
        createStandby()
        return engine
    }

    private fun createStandby(): ManagedEngine =
        factory.create(positionMs = 0, surface = null, playWhenReady = false)
            .apply { setLooping(looping) }
            .also { standby = it }

    /**
     * A ready standby, within the retry budget. A standby that failed while waiting, or doesn't get
     * ready in time, is recreated and costs an attempt. Null once the budget is spent.
     */
    private suspend fun awaitStandby(cause: StallCause): ManagedEngine? {
        while (true) {
            val delayMs = retryPolicy.acquire() ?: return null
            _state.value = WallperPlayerState.Recovering(cause)
            delay(delayMs)
            val candidate = standby ?: createStandby()
            if (awaitReady(candidate)) return candidate
            log("Standby #${candidate.id} isn't usable, recreating it")
            candidate.release()
            createStandby()
        }
    }

    /** Moves playback from [from] to the prepared [next] and replaces [from] with a new standby. */
    private fun switchTo(next: ManagedEngine, from: ManagedEngine, positionMs: Long): ManagedEngine {
        from.release() // a surface takes one producer at a time; it keeps the last frame meanwhile
        next.setSurface(surface)
        next.seekTo(positionMs)
        next.play()
        active = next
        createStandby()
        log("Switched from engine #${from.id} to #${next.id} at $positionMs ms")
        return next
    }

    /** True once [engine] is ready; false if it fails or takes too long. An error wins over readiness. */
    private suspend fun awaitReady(engine: ManagedEngine): Boolean = withTimeoutOrNull(standbyReadyTimeoutMs) {
        combine(engine.isReady, engine.error) { ready, errorCode ->
            when {
                errorCode != null -> false
                ready -> true
                else -> null
            }
        }.filterNotNull().first()
    } ?: false

    private fun fail(cause: FailureCause) {
        log("Giving up: $cause")
        releaseAll()
        _state.value = WallperPlayerState.Failed(cause)
    }

    private companion object {
        const val PAUSED_RELEASE_MS = 15_000L
        const val STANDBY_READY_TIMEOUT_MS = 2_000L

        /** The file itself can't be played; another engine won't change that. */
        val CONTENT_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )
    }
}
