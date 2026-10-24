package com.onthecrow.wallper.core.player.engine

import android.view.Surface
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One ExoPlayer instance as the supervisor drives it. Player thread only. State is exposed as flows that
 * replay their latest value, so nothing is missed by a late collector.
 */
internal interface ManagedEngine {
    val id: Int

    /** In STATE_READY: a frame is decoded and waiting, even without a surface. */
    val isReady: StateFlow<Boolean>

    /** PlaybackException code of the error that stopped this engine, if any. */
    val error: StateFlow<Int?>

    /** Frames per second of the video, 0 while unknown. */
    val frameRate: StateFlow<Float>

    /** Emits when the clip loops to its start. */
    val loopBoundaries: SharedFlow<Unit>

    /** In STATE_ENDED: the clip played to its end without looping. A seek leaves it. */
    val isEnded: StateFlow<Boolean>

    val positionMs: Long

    fun setSurface(surface: Surface?)

    fun snapshot(): EngineSnapshot

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setLooping(looping: Boolean)

    /** Idempotent. */
    fun release()
}

internal data class EngineSnapshot(
    val state: State,
    val positionMs: Long,
    /** Duration of the (clipped) item, or a negative value when unknown. */
    val durationMs: Long,
    /** Frames the decoder has output: rendered, dropped and skipped. Resets when the renderer is re-enabled. */
    val decodedFrames: Long,
) {
    enum class State { IDLE, BUFFERING, READY, ENDED }
}

internal fun interface EngineFactory {
    /** @param surface null prepares the engine off-screen: the decoder still runs and the engine gets ready. */
    fun create(positionMs: Long, surface: Surface?, playWhenReady: Boolean): ManagedEngine
}
