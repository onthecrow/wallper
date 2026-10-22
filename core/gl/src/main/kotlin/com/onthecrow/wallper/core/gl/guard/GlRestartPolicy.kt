package com.onthecrow.wallper.core.gl.guard

/**
 * How often a broken or hung GL thread may be recreated before giving up: at most [maxRestarts]
 * within [windowMs]. Separate from the player's budget: a GL failure doesn't touch the decoder.
 */
class GlRestartPolicy(
    private val nowMs: () -> Long,
    private val maxRestarts: Int = 3,
    private val windowMs: Long = 120_000,
) {
    private val restartsAtMs = ArrayDeque<Long>()

    /** Records a restart and returns true, or returns false when the limit is reached. */
    fun tryAcquire(): Boolean {
        val now = nowMs()
        while (restartsAtMs.isNotEmpty() && now - restartsAtMs.first() > windowMs) restartsAtMs.removeFirst()
        if (restartsAtMs.size >= maxRestarts) return false
        restartsAtMs.addLast(now)
        return true
    }
}
