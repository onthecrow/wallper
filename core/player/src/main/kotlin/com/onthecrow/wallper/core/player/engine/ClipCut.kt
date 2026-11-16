package com.onthecrow.wallper.core.player.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import timber.log.Timber

/**
 * Where a clip from [startUs] that is to end at [endUs] ends for the player, in microseconds: where the
 * first frame it doesn't show would have been shown, so that its last frame stays on the screen for as long
 * as it does in the video, not for as long as the clip happens to go on.
 *
 * That may be earlier than [endUs]. Media3 cuts a clip at the first frame at or after its end in decoding
 * order (`ClippingSampleStream`). In a video with B-frames that is a frame the ones shown just before it are
 * predicted from, so they come after it in the file and are cut off with it, though they belong to the
 * clip: the last frame that is shown would stay for theirs too, at every loop. And it may be later, by less
 * than a frame: an end between two frames would take the last one off the screen early.
 *
 * @param timesUs the frames' times in decoding order, from a key frame before the end to past the frames
 * that are shown before the first one at or after the end ([frameTimesAroundUs]).
 */
internal fun cutEndUs(startUs: Long, endUs: Long, timesUs: List<Long>): Long {
    var cutEndUs = endUs
    // An earlier end may cut other frames off: until the end is the first frame that isn't shown.
    while (true) {
        val cut = timesUs.indexOfFirst { it >= cutEndUs }
        if (cut < 0) break // the clip runs to the end of the video
        val firstNotShownUs = timesUs.subList(cut, timesUs.size).min()
        if (firstNotShownUs == cutEndUs) break
        cutEndUs = firstNotShownUs
    }
    // A clip of less than what one frame is predicted from is left as it is.
    return if (cutEndUs > startUs) cutEndUs else endUs
}

/**
 * The times of the frames of the video at [path] in decoding order, in microseconds, for [cutEndUs]: from
 * the key frame before [endUs] to [FRAMES_PAST_END] frames past the first one at or after it. Null when the
 * file can't be read, has no video, or that key frame is more than [MAX_FRAMES] frames back: the frames'
 * times come with their data, and that is too much of the file to read for a few frames.
 */
internal fun frameTimesAroundUs(path: String, endUs: Long): List<Long>? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: return null
        extractor.selectTrack(track)
        extractor.seekTo(endUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val timesUs = ArrayList<Long>()
        var pastEnd = 0
        while (pastEnd < FRAMES_PAST_END) {
            val timeUs = extractor.sampleTime
            if (timeUs < 0) break // the end of the track
            if (timesUs.size == MAX_FRAMES) return null
            timesUs += timeUs
            if (pastEnd > 0 || timeUs >= endUs) pastEnd++
            if (!extractor.advance()) break
        }
        timesUs
    } catch (e: Exception) { // IOException for a file it can't open, runtime ones from the native extractor
        Timber.tag(TAG).w(e, "Can't read the frames of $path around its clip's end: its loop may pause there")
        null
    } finally {
        extractor.release()
    }
}

/** More than any codec lets a frame be shown before the one it follows in the file. */
private const val FRAMES_PAST_END = 32

/** 20 s of a 30 fps video: tens of megabytes of a 4K one. */
private const val MAX_FRAMES = 600
private const val TAG = "WallperPlayer"
