package com.onthecrow.wallper.editor.export

/**
 * How long an export still takes, at the pace it has had since the first percent Transformer reported: the
 * time before it, the decoder and the encoder starting, says nothing of the rest. One per export. Free of
 * Android types: tested on the JVM.
 */
internal class ExportPace {
    private var firstProgress = NONE
    private var firstElapsedMs = 0L

    /** At [progress] percent, [elapsedMs] after the start: the time left, or null while it is too early to tell. */
    fun remainingMs(progress: Int, elapsedMs: Long): Long? {
        if (progress <= 0) return null
        if (firstProgress == NONE) {
            firstProgress = progress
            firstElapsedMs = elapsedMs
            return null
        }
        if (progress >= 100) return 0
        val done = progress - firstProgress
        val spentMs = elapsedMs - firstElapsedMs
        // A percent or two in a moment says little: Transformer's percent is whole.
        if (done < MIN_PERCENTS || spentMs < MIN_SPENT_MS) return null
        return spentMs * (100 - progress) / done
    }

    companion object {
        const val MIN_PERCENTS = 3
        const val MIN_SPENT_MS = 1_500L
        private const val NONE = -1
    }
}
