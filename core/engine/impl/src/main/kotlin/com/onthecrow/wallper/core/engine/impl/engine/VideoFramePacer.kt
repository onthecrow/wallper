package com.onthecrow.wallper.core.engine.impl.engine

/**
 * Decides when a panning video wallpaper draws: one frame per display refresh at most.
 *
 * Two things ask for a frame and know nothing of each other: the decoder's next video frame, and the
 * launcher's next page offset while its pages are swiped. Drawn each on its own they are more frames than
 * the display shows: the window's buffer queue fills up, the GL thread waits for a free buffer holding an
 * offset it read before the wait, and the picture trails the pages by several refreshes, unevenly.
 *
 * So a refresh gets one frame, with whatever is the latest by then:
 * - an offset is drawn as it comes. The launcher sends one per refresh, just after the vsync, which makes
 *   it the clock of a swipe; a second one within a refresh waits;
 * - a video frame waits for the next vsync. During a swipe it isn't drawn there either: the offset that
 *   follows the vsync draws it too, and when none follows (the swipe is over) the vsync after does.
 *
 * Thread-safe: offsets come on the main thread, video frames and vsyncs on the engine's callback thread.
 * While nothing asks, nothing runs: no vsync is waited for.
 *
 * @param awaitVsync has [onVsync] called once, at the next vsync.
 * @param draw draws a frame with the latest offset and video frame.
 */
internal class VideoFramePacer(
    private val awaitVsync: () -> Unit,
    private val draw: () -> Unit,
) {
    /** Something asked for a frame that no draw has answered yet. */
    private var pending = false
    private var vsyncAwaited = false

    /** Since the last vsync: the refresh has its frame. */
    private var drawnSinceVsync = false

    /** Since the last vsync: the launcher sent an offset, so the pages are being swiped. */
    private var offsetSinceVsync = false

    /** The launcher moved its pages. */
    @Synchronized
    fun onOffset() {
        offsetSinceVsync = true
        if (drawnSinceVsync) pending = true else drawNow()
        // The vsync tells one refresh from the next, and draws what waits when no other offset comes for it.
        awaitVsyncOnce()
    }

    /** The decoder has a new video frame. */
    @Synchronized
    fun onVideoFrame() {
        pending = true
        awaitVsyncOnce()
    }

    @Synchronized
    fun onVsync() {
        vsyncAwaited = false
        val swiping = offsetSinceVsync
        offsetSinceVsync = false
        drawnSinceVsync = false
        if (!pending) return
        if (swiping) awaitVsyncOnce() else drawNow()
    }

    private fun drawNow() {
        pending = false
        drawnSinceVsync = true
        draw()
    }

    private fun awaitVsyncOnce() {
        if (vsyncAwaited) return
        vsyncAwaited = true
        awaitVsync()
    }
}
