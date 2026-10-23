package com.onthecrow.wallper.editor.preview

/**
 * Tells when the preview player has stopped: it should be moving on (buffering, or playing) and for
 * [stallMs] its decoder put out no frame and its position stood still. It only judges; [EditorPlayer]
 * makes a new player.
 */
internal class PreviewWatchdog(private val stallMs: Long) {

    private var lastFrames = Long.MIN_VALUE
    private var lastPositionMs = Long.MIN_VALUE
    private var lastProgressAtMs = Long.MIN_VALUE

    /** One look at the player; true once it has stopped. */
    fun onSample(nowMs: Long, expectsProgress: Boolean, frames: Long, positionMs: Long): Boolean {
        val moved = frames != lastFrames || positionMs != lastPositionMs
        lastFrames = frames
        lastPositionMs = positionMs
        if (moved || !expectsProgress || lastProgressAtMs == Long.MIN_VALUE) {
            lastProgressAtMs = nowMs
            return false
        }
        return nowMs - lastProgressAtMs >= stallMs
    }

    /** A new player: it gets the whole wait. */
    fun reset(nowMs: Long) {
        lastProgressAtMs = nowMs
    }
}

/** At most [max] new players in any [windowMs]: a file that always fails isn't retried in a tight loop. */
internal class RebuildPolicy(private val max: Int, private val windowMs: Long) {

    private val rebuiltAtMs = ArrayDeque<Long>()

    /** Whether a new player may be made at [nowMs]; if so, it counts. */
    fun tryAcquire(nowMs: Long): Boolean {
        while (rebuiltAtMs.isNotEmpty() && nowMs - rebuiltAtMs.first() >= windowMs) rebuiltAtMs.removeFirst()
        if (rebuiltAtMs.size >= max) return false
        rebuiltAtMs.addLast(nowMs)
        return true
    }
}
