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
 * The editor's preview. It loads the whole video, so the trim bar can show any frame of it, and stops at the
 * end of the kept part itself. Main thread only, like the ExoPlayer inside.
 *
 * @param onError the player can't go on: the file may be gone or unreadable.
 */
@Stable
internal class EditorPlayer(context: Context, path: String, onError: (PlaybackException) -> Unit) {

    private val player: ExoPlayer = createLeanExoPlayer(context).apply {
        repeatMode = Player.REPEAT_MODE_OFF
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
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
     * The part to play. An empty one, while the length of the video is unknown, plays all of it. The first
     * one also moves the player, new at 0, to its start: the kept part of an edited wallpaper, or of one
     * after the activity is recreated, may start later.
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

    /** Plays from the playhead, or from the start of the kept part once it has played to its end. */
    fun play() {
        val trim = trim
        if (trim != null && isAtTrimEnd(player.currentPosition, trim, isVideoEnded)) seekTo(trim.startMs)
        player.play()
    }

    fun pause() = player.pause()

    /**
     * Follows playback; call it every frame while playing. Returns false once the kept part has played to
     * its end: the player has stopped there.
     */
    fun onFrame(): Boolean {
        val trim = trim
        val position = player.currentPosition
        if (trim == null || !isAtTrimEnd(position, trim, isVideoEnded)) {
            positionMs = position
            return true
        }
        player.pause()
        // The last frame may have run past the end: show the one at the end handle.
        trim.endMsOrNull?.let(::seekTo) ?: run { positionMs = position }
        return false
    }

    private val isVideoEnded get() = player.playbackState == Player.STATE_ENDED

    fun setVideoTextureView(view: TextureView?) = player.setVideoTextureView(view)

    fun release() = player.release()
}

/**
 * Whether playback at [positionMs] has played [trim] to its end, or the whole video has [ended]. An empty
 * trim (the length of the video is unknown) plays to the end of the video. Before its start playback goes
 * on: the user may be looking for a new start.
 */
internal fun isAtTrimEnd(positionMs: Long, trim: ClipRange, ended: Boolean): Boolean =
    ended || positionMs >= (trim.endMsOrNull ?: Long.MAX_VALUE)

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
 * Plays [player] up to the end of [trim] while [play] and the screen is in the foreground, following it
 * frame by frame; pauses it otherwise. [onEnd] comes when it stops at the end.
 */
@Composable
internal fun PlaybackEffect(player: EditorPlayer, trim: ClipRange, play: Boolean, onEnd: () -> Unit) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = play && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val currentOnEnd by rememberUpdatedState(onEnd)
    SideEffect {
        player.setTrim(trim)
    }
    LaunchedEffect(player, playing) {
        if (!playing) {
            player.pause()
            return@LaunchedEffect
        }
        player.play()
        do {
            val isPlaying = withFrameMillis { player.onFrame() }
        } while (isPlaying)
        currentOnEnd()
    }
}
