package com.onthecrow.wallper.core.player.supervisor

import android.view.Surface
import com.onthecrow.wallper.core.player.engine.EngineFactory
import com.onthecrow.wallper.core.player.engine.EngineSnapshot
import com.onthecrow.wallper.core.player.engine.ManagedEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An engine whose decoder makes one frame of progress per snapshot while [healthy] and playing.
 * Lifecycle calls also go to [journal], shared by every engine of one factory, to check their order.
 */
internal class FakeEngine(
    override val id: Int,
    val startPositionMs: Long,
    val createdOnSurface: Surface? = null,
    val createdPlaying: Boolean = true,
    private val journal: MutableList<String> = mutableListOf(),
) : ManagedEngine {
    var surface = createdOnSurface
        private set
    var playing = createdPlaying
    var healthy = true
    var state = EngineSnapshot.State.READY
    var durationMs = 10_000L
    var released = false
    val actions = mutableListOf<String>()

    private var frames = 0L
    override var positionMs = startPositionMs

    override val isReady = MutableStateFlow(true)
    override val error = MutableStateFlow<Int?>(null)
    override val frameRate = MutableStateFlow(30f)
    override val loopBoundaries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        journal += "#$id setSurface"
    }

    override fun snapshot(): EngineSnapshot {
        if (healthy && playing && !released) {
            frames++
            positionMs += 250
        }
        return EngineSnapshot(state, positionMs, durationMs, frames)
    }

    override fun play() {
        playing = true
        actions += "play"
        journal += "#$id play"
    }

    override fun pause() {
        playing = false
        actions += "pause"
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
        journal += "#$id seekTo $positionMs"
    }

    override fun release() {
        if (released) return
        released = true
        journal += "#$id release"
    }
}

/** Creates [FakeEngine]s; [configure] sets up each one by its 1-based index. */
internal class FakeEngineFactory(
    private val configure: FakeEngine.(index: Int) -> Unit = {},
) : EngineFactory {
    val created = mutableListOf<FakeEngine>()
    val journal = mutableListOf<String>()

    override fun create(positionMs: Long, surface: Surface?, playWhenReady: Boolean): ManagedEngine =
        FakeEngine(created.size + 1, positionMs, surface, playWhenReady, journal).also {
            created += it
            it.configure(created.size)
        }
}
