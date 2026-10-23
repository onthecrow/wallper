package com.onthecrow.wallper.core.model

/**
 * The played part of a video, in milliseconds from its start. An end that isn't after the start means the
 * video plays to its end: its length wasn't known when it was cut.
 */
data class ClipRange(
    val startMs: Long,
    val endMs: Long,
) {
    /** [endMs], or null when the clip runs to the end of the video. */
    val endMsOrNull: Long? get() = endMs.takeIf { it > startMs }
}
