package com.onthecrow.wallper.core.player.supervisor

import com.onthecrow.wallper.core.player.StallCause
import com.onthecrow.wallper.core.player.engine.EngineSnapshot
import com.onthecrow.wallper.core.player.engine.ManagedEngine
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Watches one engine and reports when it stalls: no decoded frames, endless buffering, no first frame,
 * a loop that never happens, or an error. Only detects; what to do about it is the supervisor's call.
 * Knows nothing about GL: a frozen screen with a healthy decoder is GL's job.
 */
internal class StallWatchdog(private val clock: () -> Long) {

    /** Suspends while [engine] plays fine; returns why it stalled. */
    suspend fun watch(engine: ManagedEngine): StallCause = coroutineScope {
        val session = Session(engine)
        val loops = launch { engine.loopBoundaries.collect { session.loopBoundaryAtMs = clock() } }
        val failure = async { StallCause.Failed(engine.error.filterNotNull().first()) }
        try {
            monitor(session, failure)
        } finally {
            loops.cancel()
            failure.cancel()
        }
    }

    private suspend fun monitor(session: Session, failure: Deferred<StallCause.Failed>): StallCause {
        while (true) {
            // An error ends the wait right away; otherwise check progress every tick.
            withTimeoutOrNull(TICK_MS) { failure.await() }?.let { return it }
            session.tick()?.let { return it }
        }
    }

    private inner class Session(private val engine: ManagedEngine) {
        var loopBoundaryAtMs = Long.MIN_VALUE / 2

        private var lastDecodedFrames = engine.snapshot().decodedFrames
        private var lastProgressAtMs = clock()
        private var awaitingFirstFrame = true

        /** One tick; returns a cause once the engine stalled. */
        fun tick(): StallCause? {
            val snapshot = engine.snapshot()
            val now = clock()
            if (snapshot.decodedFrames != lastDecodedFrames) {
                lastDecodedFrames = snapshot.decodedFrames
                lastProgressAtMs = now
                awaitingFirstFrame = false
                return null
            }
            return if (now - lastProgressAtMs < thresholdMs(snapshot, now)) null else classify(snapshot)
        }

        private fun thresholdMs(snapshot: EngineSnapshot, now: Long): Long {
            val base = when {
                awaitingFirstFrame -> FIRST_FRAME_TIMEOUT_MS
                snapshot.state == EngineSnapshot.State.BUFFERING -> BUFFERING_TIMEOUT_MS
                else -> (frameIntervalMs() * 5).coerceIn(MIN_DECODER_STALL_MS, MAX_DECODER_STALL_MS)
            }
            // After a loop boundary the decoder may need extra time to reach the clip start again.
            val loopGrace = if (now - loopBoundaryAtMs < base + LOOP_GRACE_MS) LOOP_GRACE_MS else 0
            return base + loopGrace
        }

        private fun classify(snapshot: EngineSnapshot): StallCause = when {
            awaitingFirstFrame -> StallCause.NoFirstFrame
            snapshot.state == EngineSnapshot.State.BUFFERING -> StallCause.StuckBuffering
            snapshot.durationMs > 0 && snapshot.positionMs >= snapshot.durationMs - CLIP_END_TOLERANCE_MS ->
                StallCause.StuckAtClipEnd
            else -> StallCause.DecoderStalled
        }

        private fun frameIntervalMs(): Long {
            val fps = engine.frameRate.value
            return if (fps > 0f) (1_000f / fps).toLong().coerceIn(16, 100) else DEFAULT_FRAME_INTERVAL_MS
        }
    }

    private companion object {
        const val TICK_MS = 250L
        const val DEFAULT_FRAME_INTERVAL_MS = 33L
        const val MIN_DECODER_STALL_MS = 400L
        const val MAX_DECODER_STALL_MS = 1_000L
        const val FIRST_FRAME_TIMEOUT_MS = 2_000L
        const val BUFFERING_TIMEOUT_MS = 1_500L
        const val LOOP_GRACE_MS = 1_000L
        const val CLIP_END_TOLERANCE_MS = 50L
    }
}
