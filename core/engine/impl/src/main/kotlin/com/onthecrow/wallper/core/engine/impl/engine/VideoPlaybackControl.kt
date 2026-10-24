package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.VideoWallpaperSettings
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.core.player.WallperPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Plays a video wallpaper's [player] while the wallpaper is visible, unless the user wants it to save
 * battery and the phone [savingPower]; a pause keeps the frame on screen. Each time the wallpaper is shown
 * again, [VideoPlayback] decides whether the video goes on or starts over, and whether it loops.
 *
 * Decides on [scope]'s thread; [setVisible] may come from any thread.
 */
internal class VideoPlaybackControl(
    private val player: WallperPlayer,
    settings: Flow<VideoWallpaperSettings>,
    savingPower: Flow<Boolean>,
    scope: CoroutineScope,
) {
    /** [shows] counts the times the wallpaper became visible: each one is a new start for some modes. */
    private data class Visibility(val visible: Boolean, val shows: Int)

    private data class Command(val play: Boolean, val show: Int, val playback: VideoPlayback)

    private val visibility = MutableStateFlow(Visibility(visible = true, shows = 0))

    /** The show the player last played for. A fresh player is at the clip start already. */
    private var playedShow = 0

    init {
        scope.launch {
            combine(visibility, settings, savingPower) { visibility, settings, saving ->
                Command(
                    play = visibility.visible && !(settings.pausesToSaveBattery && saving),
                    show = visibility.shows,
                    playback = settings.playback,
                )
            }
                .distinctUntilChanged()
                .collect(::apply)
        }
    }

    fun setVisible(visible: Boolean) {
        visibility.update { current ->
            if (visible && !current.visible) Visibility(visible = true, shows = current.shows + 1) else current.copy(visible = visible)
        }
    }

    private fun apply(command: Command) {
        player.setLooping(command.playback != VideoPlayback.ONCE)
        if (!command.play) {
            player.pause()
            return
        }
        // Coming back from Battery Saver isn't a new show: the video goes on.
        if (command.show != playedShow && command.playback != VideoPlayback.LOOP) player.seekToStart()
        playedShow = command.show
        player.play()
    }
}
