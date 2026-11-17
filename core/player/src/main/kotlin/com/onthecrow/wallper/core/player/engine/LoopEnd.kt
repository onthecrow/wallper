package com.onthecrow.wallper.core.player.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import timber.log.Timber

/**
 * Where a loop of the clip ends, in microseconds; null plays to the end of the file.
 *
 * It is the clip's end, but never after the end of the video track ([videoEndUs], null when unknown).
 * The next loop starts when the player's period is over, and a period lasts as long as the longest track
 * of the file. With a sound track that outlasts the video (it is never played), or a clip that ends past
 * the last frame, the last frame would stay on the screen for the difference, at every loop.
 */
internal fun loopEndUs(clipStartMs: Long?, clipEndMs: Long?, videoEndUs: Long?): Long? {
    val clipEndUs = clipEndMs?.let { it * US_PER_MS }
    val clipStartUs = (clipStartMs ?: 0) * US_PER_MS
    // A video track that is over before the clip starts says nothing about this clip.
    if (videoEndUs == null || videoEndUs <= clipStartUs) return clipEndUs
    return if (clipEndUs == null) videoEndUs else minOf(clipEndUs, videoEndUs)
}

/**
 * Where the video of the file at [path] ends, in microseconds, as its container says: the longest video
 * track, should there be several. Null when the container doesn't say or the file can't be read; the
 * player reports a broken file itself.
 */
internal fun videoTrackEndUs(path: String): Long? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(path)
        (0 until extractor.trackCount)
            .map(extractor::getTrackFormat)
            .filter { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            .filter { it.containsKey(MediaFormat.KEY_DURATION) }
            .maxOfOrNull { it.getLong(MediaFormat.KEY_DURATION) }
            ?.takeIf { it > 0 }
    } catch (e: Exception) { // IOException for a file it can't open, runtime ones from the native extractor
        Timber.tag(TAG).w(e, "Can't read where the video of $path ends: its loop may pause at the end")
        null
    } finally {
        extractor.release()
    }
}

private const val US_PER_MS = 1_000L
private const val TAG = "WallperPlayer"
