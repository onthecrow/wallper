package com.onthecrow.wallper.editor.preview

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.player.createLeanExoPlayer
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * The editor's preview. It loads the whole video, so the trim bar can show any frame of it, and loops the
 * kept part itself. Main thread only, like the ExoPlayer inside.
 *
 * A decoder that fails or stops (Samsung's have) would freeze the preview for good: [watch] spots that and
 * plays on with a new ExoPlayer from the same moment, at most [MAX_REBUILDS] times a minute.
 */
@Stable
@OptIn(UnstableApi::class) // decoder counters, scrubbing parameters, analytics
internal class EditorPlayer(private val context: Context, private val path: String) {

    private val listener = object : Player.Listener, AnalyticsListener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "The editor preview of $path failed: ${error.errorCodeName}")
            // Not from inside the failed player's own callback: the next watch makes the new one.
            failed = true
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.i("The editor preview decodes with $decoderName on ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    private var player: ExoPlayer = newPlayer()

    /**
     * Counts the ExoPlayers so far. The preview makes a new TextureView for each: a decoder that stopped may
     * still hold the old one's surface.
     */
    var generation by mutableIntStateOf(0)
        private set

    /** The moment on screen, in ms. Follows playback every frame and every seek. */
    var positionMs by mutableLongStateOf(0L)
        private set

    private var trim: ClipRange? = null
    private var playing = false
    private var scrubbing = false
    private var failed = false
    private val watchdog = PreviewWatchdog(STALL_MS)
    private val rebuilds = RebuildPolicy(MAX_REBUILDS, REBUILD_WINDOW_MS)

    /**
     * The part to loop. An empty one, while the length of the video is unknown, loops all of it. The first
     * one also moves the player, new at 0, to its start: after the activity is recreated, the kept part
     * may start later.
     */
    fun setTrim(trim: ClipRange) {
        val isFirst = this.trim == null
        this.trim = trim
        if (isFirst && trim.endMs > trim.startMs) seekTo(trim.startMs)
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        this.positionMs = positionMs
    }

    /** Tunes the player for the many seeks of a drag. It holds playback back meanwhile. */
    fun setScrubbing(enabled: Boolean) {
        scrubbing = enabled
        player.setScrubbingModeEnabled(enabled)
    }

    fun play() {
        playing = true
        player.play()
    }

    fun pause() {
        playing = false
        player.pause()
    }

    /** Follows playback; call it every frame while playing. Outside the kept part it starts it over. */
    fun onFrame() {
        val trim = trim ?: return
        val position = player.currentPosition
        if (restartsLoop(position, trim, ended = player.playbackState == Player.STATE_ENDED)) {
            seekTo(trim.startMs)
        } else {
            positionMs = position
        }
    }

    /**
     * Checks that the player is alive; call it a few times a second while the preview is shown. One that
     * failed, or should be moving on and hasn't for [STALL_MS], is replaced.
     */
    fun watch(nowMs: Long) {
        val state = player.playbackState
        val expectsProgress = state == Player.STATE_BUFFERING ||
            state == Player.STATE_READY && player.playWhenReady &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        val counters = player.videoDecoderCounters?.apply { ensureUpdated() }
        val frames = counters?.let {
            it.renderedOutputBufferCount.toLong() + it.droppedBufferCount + it.skippedOutputBufferCount
        } ?: 0L
        val stalled = watchdog.onSample(nowMs, expectsProgress, frames, player.currentPosition)
        when {
            failed -> rebuild(nowMs, "failed")
            stalled -> rebuild(nowMs, "stopped in state $state at ${player.currentPosition} ms after $frames frames")
        }
    }

    fun setVideoTextureView(view: TextureView) = player.setVideoTextureView(view)

    /** Detaches [view] if the current player shows on it; an older view of a replaced player is left alone. */
    fun clearVideoTextureView(view: TextureView) = player.clearVideoTextureView(view)

    fun release() {
        player.removeListener(listener)
        player.removeAnalyticsListener(listener)
        player.release()
    }

    private fun rebuild(nowMs: Long, reason: String) {
        if (!rebuilds.tryAcquire(nowMs)) return
        Timber.e("The editor preview of $path $reason: playing on with a new player from $positionMs ms")
        release()
        failed = false
        player = newPlayer().apply {
            seekTo(positionMs)
            setScrubbingModeEnabled(scrubbing)
            playWhenReady = playing
        }
        watchdog.reset(nowMs)
        generation++
    }

    private fun newPlayer(): ExoPlayer = createLeanExoPlayer(context).apply {
        // The loop over the kept part is ours: the player's own would run to the end of the video.
        repeatMode = Player.REPEAT_MODE_OFF
        setScrubbingModeParameters(GENTLE_SCRUBBING)
        addListener(listener)
        addAnalyticsListener(listener)
        setMediaItem(MediaItem.fromUri(path.toUri()))
        prepare()
    }

    private companion object {
        /**
         * Scrubbing without the codec tricks it does by default (skipping the flush between seeks, decode-only
         * input, a raised operating rate): vendor decoders may stop over them. What stays is the held
         * playback and the scheduling.
         */
        val GENTLE_SCRUBBING: ScrubbingModeParameters = ScrubbingModeParameters.DEFAULT.buildUpon()
            .setAllowSkippingMediaCodecFlush(false)
            .setUseDecodeOnlyFlag(false)
            .setShouldIncreaseCodecOperatingRate(false)
            .build()

        /** Long enough for a 4K seek from a distant key frame on a slow phone. */
        const val STALL_MS = 3_000L
        const val MAX_REBUILDS = 3
        const val REBUILD_WINDOW_MS = 60_000L
    }
}

/**
 * Whether playback at [positionMs] has left [trim] and goes back to its start: past its end, at the end of
 * the video, or before its start. An empty trim (the length of the video is unknown) keeps all of it.
 */
internal fun restartsLoop(positionMs: Long, trim: ClipRange, ended: Boolean): Boolean {
    val endMs = if (trim.endMs > trim.startMs) trim.endMs else Long.MAX_VALUE
    return ended || positionMs < trim.startMs || positionMs >= endMs
}

/** A player for the video at [path], released when it leaves the composition. */
@Composable
internal fun rememberEditorPlayer(path: String): EditorPlayer {
    val context = LocalContext.current
    val player = remember(context, path) { EditorPlayer(context, path) }
    DisposableEffect(player) {
        onDispose(player::release)
    }
    return player
}

/**
 * Loops [trim] of [player] while [play] and the screen is in the foreground, following it frame by frame;
 * pauses it otherwise. While the screen is in the foreground it also keeps an eye on the player.
 */
@Composable
internal fun PlaybackEffect(player: EditorPlayer, trim: ClipRange, play: Boolean) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val playing = play && isStarted
    SideEffect {
        player.setTrim(trim)
    }
    LaunchedEffect(player, playing) {
        if (!playing) {
            player.pause()
            return@LaunchedEffect
        }
        player.play()
        while (true) {
            withFrameMillis { player.onFrame() }
        }
    }
    LaunchedEffect(player, isStarted) {
        if (!isStarted) return@LaunchedEffect
        while (true) {
            delay(WATCH_INTERVAL_MS)
            player.watch(SystemClock.uptimeMillis())
        }
    }
}

private const val WATCH_INTERVAL_MS = 250L
