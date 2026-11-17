package com.onthecrow.wallper.core.gl

/**
 * Takes a video texture to the newest frame its decoder has queued.
 *
 * A SurfaceTexture hands its frames out one per `updateTexImage`, oldest first, and keeps the others. A
 * draw that latched only one would leave every frame that came while another waited in the queue for good:
 * the video is then shown that much late, and plays fast whenever something else asks for draws (a swipe
 * between the launcher's pages), until the queue is empty. Free of Android types, so it is tested on the JVM.
 */
internal object NewestFrame {

    /** More than a decoder queues ahead; ends the loop should frames come as fast as they are latched. */
    const val MAX_LATCHES = 16

    /**
     * @param previousNs the timestamp of the frame the texture had.
     * @param latchNext latches the next queued frame, if there is one, and returns the timestamp of the
     * frame in the texture.
     * @return the timestamp of the frame in the texture now.
     */
    inline fun latch(previousNs: Long, latchNext: () -> Long): Long {
        var currentNs = latchNext()
        // Nothing new: nothing waits behind it either.
        if (currentNs == previousNs) return currentNs
        repeat(MAX_LATCHES - 1) {
            val nextNs = latchNext()
            if (nextNs == currentNs) return currentNs
            currentNs = nextNs
        }
        return currentNs
    }
}
