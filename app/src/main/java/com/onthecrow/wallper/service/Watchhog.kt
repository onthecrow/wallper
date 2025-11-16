package com.onthecrow.wallper.service

import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlinx.coroutines.Runnable
import timber.log.Timber
import javax.inject.Inject

/**
 * A utility class that acts as a "watchdog" for an [ExoPlayer] instance.
 *
 * Its primary purpose is to monitor the video rendering process and detect if it gets stuck.
 * When a video frame is about to be rendered, it schedules a "kick" action to be executed
 * after a short delay. If another frame is rendered before the delay expires, the action is
 * rescheduled. If no new frames are rendered within the timeout, it assumes the player is
 * stuck and executes the "kick" action, which involves re-preparing the player to resume playback.
 *
 * This is a workaround for potential issues on some devices where video playback might freeze
 * without the player state changing to an error or paused state.
 *
 * It listens to:
 * - [VideoFrameMetadataListener]: To know when frames are being rendered.
 * - [Player.Listener]: To track the playing state.
 * - [AnalyticsListener]: To know when the video component is disabled.
 *
 * @see Player.Listener
 * @see VideoFrameMetadataListener
 * @see AnalyticsListener
 */
@UnstableApi
class Watchhog @Inject constructor(
) : Player.Listener, VideoFrameMetadataListener, AnalyticsListener {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var isPlaying = false
    private var forceReinitAction: Runnable = Runnable { }

    /**
     * Attaches the Watchhog to an [ExoPlayer] instance to monitor its state.
     *
     * This function sets up the necessary listeners ([VideoFrameMetadataListener], [Player.Listener],
     * and [AnalyticsListener]) on the provided player. It also configures a
     * `forceReinitAction` that will "kick" (re-initialize) the player if it gets stuck.
     * The "kick" is triggered if a new video frame isn't rendered within a specific timeout.
     *
     * @param player The [ExoPlayer] instance to monitor.
     */
    fun attachPlayer(player: ExoPlayer) {
        runCatching { player.clearVideoFrameMetadataListener(this) }.onFailure { Timber.e(it) }
        runCatching { player.setVideoFrameMetadataListener(this) }.onFailure { Timber.e(it) }
        player.addListener(this)
        player.addAnalyticsListener(this)
        this.forceReinitAction = Runnable {
            if (isPlaying) player.kickPlayer()
        }
        Timber.d("Player attached")
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?
    ) {
        handler.removeCallbacks(forceReinitAction)
        // todo count delay based on fps
        handler.postDelayed(forceReinitAction, 100)
    }

    override fun onVideoDisabled(
        eventTime: AnalyticsListener.EventTime,
        decoderCounters: DecoderCounters
    ) {
        Timber.d("onVideoDisabled()")
        handler.removeCallbacks(forceReinitAction)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Timber.d("Is playing changed: $isPlaying")
        handler.removeCallbacks(forceReinitAction)
        this.isPlaying = isPlaying
    }

    private fun ExoPlayer.kickPlayer() {
        val position = currentPosition
        val shouldPlay = playWhenReady
        stop()
        prepare()
        seekTo(position)
        playWhenReady = shouldPlay
        Timber.d("Kicked player")
    }
}