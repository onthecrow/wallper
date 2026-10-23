package com.onthecrow.wallper.editor.preview

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.player.createLeanExoPlayer
import timber.log.Timber

/**
 * The editor's preview. It loads the whole video, so the trim bar can show any frame of it, and loops the
 * kept part itself. Main thread only, like the ExoPlayer inside.
 *
 * @param onError the player can't go on: the file may be gone or unreadable.
 */
@Stable
internal class EditorPlayer(context: Context, path: String, onError: (PlaybackException) -> Unit) {

    private val player: ExoPlayer = createLeanExoPlayer(context).apply {
        // The loop over the kept part is ours: the player's own would run to the end of the video.
        repeatMode = Player.REPEAT_MODE_OFF
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // TODO Report to Sentry or Crashlytics once one is set up: Timber.e reaches Diagnostics.reporter,
                //  which only writes to logcat for now.
                Timber.e(error, "The editor preview can't play $path: ${error.errorCodeName}")
                onError(error)
            }
        })
        setMediaItem(MediaItem.fromUri(path.toUri()))
        prepare()
    }

    /** The moment on screen, in ms. Follows playback every frame and every seek. */
    var positionMs by mutableLongStateOf(0L)
        private set

    private var trim: ClipRange? = null

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
    @OptIn(UnstableApi::class)
    fun setScrubbing(enabled: Boolean) {
        player.setScrubbingModeEnabled(enabled)
    }

    fun play() = player.play()

    fun pause() = player.pause()

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

    fun setVideoTextureView(view: TextureView?) = player.setVideoTextureView(view)

    fun release() = player.release()
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
internal fun rememberEditorPlayer(path: String, onError: (PlaybackException) -> Unit): EditorPlayer {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    val player = remember(context, path) { EditorPlayer(context, path) { currentOnError(it) } }
    DisposableEffect(player) {
        onDispose(player::release)
    }
    return player
}

/**
 * Loops [trim] of [player] while [play] and the screen is in the foreground, following it frame by frame;
 * pauses it otherwise.
 */
@Composable
internal fun PlaybackEffect(player: EditorPlayer, trim: ClipRange, play: Boolean) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = play && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
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
}
