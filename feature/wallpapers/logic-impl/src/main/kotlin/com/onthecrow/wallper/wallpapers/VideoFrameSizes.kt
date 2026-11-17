package com.onthecrow.wallper.wallpapers

import android.media.MediaExtractor
import android.media.MediaFormat
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** How big a video's frames are as shown, after its rotation, read from its file; null if they can't be read. */
internal fun interface VideoFrameSizes {
    suspend fun of(path: String): FrameSize?

    data class FrameSize(val width: Int, val height: Int)

    companion object {
        /** Knows none: what tests without optimised videos from before v9 need. */
        val NONE = VideoFrameSizes { null }
    }
}

internal class ExtractorVideoFrameSizes @Inject constructor(
    private val dispatchersProvider: DispatchersProvider,
) : VideoFrameSizes {
    override suspend fun of(path: String): VideoFrameSizes.FrameSize? = withContext(dispatchersProvider.io) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            (0 until extractor.trackCount)
                .map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?.let { format ->
                    val width = format.getInteger(MediaFormat.KEY_WIDTH)
                    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                    if (rotation % 180 == 0) VideoFrameSizes.FrameSize(width, height) else VideoFrameSizes.FrameSize(height, width)
                }
        } catch (error: Exception) {
            Timber.w(error, "Can't read the size of the optimised video: all of it is shown")
            null
        } finally {
            extractor.release()
        }
    }
}
