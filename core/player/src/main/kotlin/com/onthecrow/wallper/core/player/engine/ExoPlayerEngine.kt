package com.onthecrow.wallper.core.player.engine

import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** [ManagedEngine] over one ExoPlayer. Player thread only. */
@UnstableApi
internal class ExoPlayerEngine(
    override val id: Int,
    private val player: ExoPlayer,
) : ManagedEngine, Player.Listener, AnalyticsListener {

    private val _isReady = MutableStateFlow(false)
    override val isReady = _isReady.asStateFlow()

    private val _error = MutableStateFlow<Int?>(null)
    override val error = _error.asStateFlow()

    private val _frameRate = MutableStateFlow(0f)
    override val frameRate = _frameRate.asStateFlow()

    private val _loopBoundaries = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val loopBoundaries = _loopBoundaries.asSharedFlow()

    private var released = false

    init {
        player.addListener(this)
        player.addAnalyticsListener(this)
    }

    override val positionMs: Long get() = player.currentPosition

    override fun setSurface(surface: Surface?) {
        if (!released) player.setVideoSurface(surface)
    }

    override fun snapshot(): EngineSnapshot {
        val counters = player.videoDecoderCounters?.apply { ensureUpdated() }
        val decodedFrames = counters?.let {
            it.renderedOutputBufferCount.toLong() + it.droppedBufferCount + it.skippedOutputBufferCount
        } ?: 0L
        return EngineSnapshot(
            state = when (player.playbackState) {
                Player.STATE_BUFFERING -> EngineSnapshot.State.BUFFERING
                Player.STATE_READY -> EngineSnapshot.State.READY
                Player.STATE_ENDED -> EngineSnapshot.State.ENDED
                else -> EngineSnapshot.State.IDLE
            },
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: -1,
            decodedFrames = decodedFrames,
        )
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun release() {
        if (released) return
        released = true
        player.removeListener(this)
        player.removeAnalyticsListener(this)
        player.release()
        Timber.tag(TAG).d("Engine #$id released")
    }

    // ---------- listeners (player thread) ----------

    override fun onPlaybackStateChanged(playbackState: Int) {
        _isReady.value = playbackState == Player.STATE_READY
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.tag(TAG).w(error, "Engine #$id error ${error.errorCodeName}")
        _error.value = error.errorCode
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) _loopBoundaries.tryEmit(Unit)
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        if (format.frameRate > 0f) _frameRate.value = format.frameRate
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Timber.tag(TAG).d("Engine #$id decoder $decoderName initialized in $initializationDurationMs ms")
    }

    private companion object {
        const val TAG = "WallperPlayer"
    }
}
