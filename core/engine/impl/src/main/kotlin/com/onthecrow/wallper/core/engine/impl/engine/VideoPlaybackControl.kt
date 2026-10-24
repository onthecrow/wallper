package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.VideoWallpaperSettings
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.core.player.WallperPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch

/**
 * Plays a video wallpaper's [player] while the wallpaper is visible, unless the user wants it to save
 * battery and the phone [savingPower]; a pause keeps the frame on screen. [VideoPlayback] decides whether
 * the video loops, and whether it starts over the first time it plays after the screen was off: a new
 * look at the phone. Coming back to it from an app or from Battery Saver isn't one: the video goes on.
 *
 * Decides on [scope]'s thread; [setVisible] may come from any thread.
 */
internal class VideoPlaybackControl(
    private val player: WallperPlayer,
    settings: Flow<VideoWallpaperSettings>,
    savingPower: Flow<Boolean>,
    screenOffs: Flow<Unit>,
    scope: CoroutineScope,
) {
    private data class Command(val play: Boolean, val wake: Int, val playback: VideoPlayback)

    private val visible = MutableStateFlow(true)

    /** The wake the player last played in. A fresh player is at the clip start already. */
    private var playedWake = 0

    init {
        // Each screen-off begins a new wake.
        val wakes = screenOffs.runningFold(0) { count, _ -> count + 1 }
        scope.launch {
            combine(visible, settings, savingPower, wakes) { visible, settings, saving, wake ->
                Command(
                    play = visible && !(settings.pausesToSaveBattery && saving),
                    wake = wake,
                    playback = settings.playback,
                )
            }
                .distinctUntilChanged()
                .collect(::apply)
        }
    }

    fun setVisible(visible: Boolean) {
        this.visible.value = visible
    }

    private fun apply(command: Command) {
        player.setLooping(command.playback != VideoPlayback.ONCE)
        if (!command.play) {
            player.pause()
            return
        }
        if (command.wake != playedWake && command.playback != VideoPlayback.LOOP) player.seekToStart()
        playedWake = command.wake
        player.play()
    }
}
