package com.onthecrow.wallper.core.player.supervisor

/**
 * Limits how many replacement engines get prepared, so a device that can't play a file doesn't retry
 * forever. The first one comes right away, later ones after growing delays. A request more than
 * [resetAfterMs] after the last granted one starts the count over: playback held up in between.
 */
internal class RetryPolicy(
    private val clock: () -> Long,
    private val delaysMs: List<Long> = DEFAULT_DELAYS_MS,
    private val resetAfterMs: Long = DEFAULT_RESET_AFTER_MS,
) {
    private var attempts = 0
    private var lastGrantedAtMs = Long.MIN_VALUE / 2

    /** Takes one attempt and returns the delay before it, or null when the budget is spent. */
    fun acquire(): Long? {
        val now = clock()
        if (now - lastGrantedAtMs >= resetAfterMs) attempts = 0
        val delay = delaysMs.getOrNull(attempts) ?: return null
        attempts++
        lastGrantedAtMs = now
        return delay
    }

    companion object {
        /** 4 attempts: right away, then after 1, 3 and 5 seconds. */
        val DEFAULT_DELAYS_MS = listOf(0L, 1_000L, 3_000L, 5_000L)
        const val DEFAULT_RESET_AFTER_MS = 30_000L
    }
}
