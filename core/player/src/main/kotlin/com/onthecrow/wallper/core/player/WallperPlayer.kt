package com.onthecrow.wallper.core.player

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * A self-healing video player for one wallpaper. To its consumer it is a plain player; inside it replaces
 * its ExoPlayer engine on stalls and codec errors, keeping the last frame on the surface meanwhile, and
 * reports [WallperPlayerState.Failed] when there is no point in trying further.
 *
 * While paused for long it frees the decoder and starts a fresh one on the next [play].
 * Every method is thread-safe.
 */
interface WallperPlayer {

    val state: StateFlow<WallperPlayerState>

    /**
     * Where to render; may change at any time, e.g. after the GL context was recreated. Null detaches.
     * Like ExoPlayer's, blocks until the decoder has let go of the previous surface (at most ~2 s), so the
     * caller may release that surface right after.
     */
    fun setVideoSurface(surface: Surface?)

    fun play()

    fun pause()

    /** Blocks the caller for at most a second. */
    fun release()
}

/** What to play: a local file, optionally clipped. */
data class PlayerMedia(
    val path: String,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
)

sealed interface WallperPlayerState {
    /** Not started or paused. */
    data object Idle : WallperPlayerState

    data object Playing : WallperPlayerState

    /** The active engine stalled; switching to the standby one. */
    data class Recovering(val cause: StallCause) : WallperPlayerState

    /** Further attempts are pointless; show the placeholder. */
    data class Failed(val cause: FailureCause) : WallperPlayerState
}

/** Why an engine stopped making progress. */
sealed interface StallCause {
    /** Ready, but the decoder outputs nothing. */
    data object DecoderStalled : StallCause

    data object StuckBuffering : StallCause

    /** Nothing decoded since the engine was started or resumed. */
    data object NoFirstFrame : StallCause

    /** The loop to the next iteration never happened. */
    data object StuckAtClipEnd : StallCause

    /** The engine reported a [PlaybackException][androidx.media3.common.PlaybackException] code. */
    data class Failed(val errorCode: Int) : StallCause
}

sealed interface FailureCause {
    /** The file itself can't be played on this device. */
    data class ContentBroken(val errorCode: Int) : FailureCause

    /** The engine stalled with no standby left: the retry budget is spent. */
    data class RetriesExhausted(val lastCause: StallCause) : FailureCause
}
