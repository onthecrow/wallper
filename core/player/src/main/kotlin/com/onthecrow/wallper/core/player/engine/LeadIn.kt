package com.onthecrow.wallper.core.player.engine

/**
 * The lead-in of a clip that starts off a key frame: the frames from that key frame up to the clip's start.
 * The decoder needs them to decode what follows; none of them is ever shown.
 *
 * A loop follows the last one in the same decoder session ([ExoEngineFactory] asks Media3 not to flush in
 * between), and the lead-in's own times lie before the end of the loop that was just played: the decoder
 * would see time run backwards. So the lead-in of a stream that follows another is queued with times of
 * its own, a microsecond apart, right after the latest frame of the stream before, and always before the
 * clip's start, which is how it is told from the clip when it comes out of the decoder.
 *
 * Frame times are the renderer's, in microseconds: the media's plus the stream's offset.
 *
 * @param clipStartUs where the clip starts in the media, in microseconds.
 */
internal class LeadIn(private val clipStartUs: Long) {

    private var latestQueuedUs = NONE
    private var latestLeadInUs = NONE

    /** Whether the frame at [timeUs], of the stream at [streamOffsetUs], is before the clip's start. */
    fun contains(timeUs: Long, streamOffsetUs: Long): Boolean = timeUs - streamOffsetUs < clipStartUs

    /** The decoder was flushed: what comes next follows nothing. */
    fun onPositionReset() {
        latestQueuedUs = NONE
        latestLeadInUs = NONE
    }

    /** Another stream follows the one that was read, in the same decoder session. */
    fun onStreamChanged() {
        latestLeadInUs = latestQueuedUs
    }

    /**
     * The time to queue the frame at [timeUs] with: its own, or, for the lead-in of a stream that follows
     * another, the next one after everything queued so far.
     */
    fun queueTimeUs(timeUs: Long, streamOffsetUs: Long): Long {
        if (latestLeadInUs == NONE || !contains(timeUs, streamOffsetUs)) return timeUs
        // Never the clip's start or later, however long the lead-in and however close the last loop ended.
        latestLeadInUs = minOf(latestLeadInUs + 1, streamOffsetUs + clipStartUs - 1)
        return latestLeadInUs
    }

    /** A frame was queued with [timeUs]. */
    fun onQueued(timeUs: Long) {
        latestQueuedUs = maxOf(latestQueuedUs, timeUs)
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
