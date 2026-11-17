package com.onthecrow.wallper.core.player.engine

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.nio.ByteBuffer

/**
 * The video renderer of a clip that starts off a key frame and is played as if it started on one
 * (`ClippingConfiguration.startsAtKeyFrame`, see [ExoEngineFactory]): Media3 then loops it without
 * flushing the decoder, and hands this renderer the clip's [LeadIn] at every loop as if it were part of
 * the clip. Here it is decoded and kept off the screen.
 *
 * From Android 14 the lead-in is queued as decode-only: the decoder gives no frames back for it, so it
 * runs through the lead-in while the last frames of the loop still wait to be shown, and the next loop
 * starts on time. Older Androids get every frame back and skip the lead-in then, one frame at a time:
 * a long lead-in still holds the loop's last frame, but the loop's first frames are never lost.
 */
@UnstableApi
internal class LeadInVideoRenderer(builder: Builder, clipStartUs: Long) : MediaCodecVideoRenderer(builder) {

    private val leadIn = LeadIn(clipStartUs)

    override fun onStreamChanged(
        formats: Array<out Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
        leadIn.onStreamChanged()
    }

    // Also follows onStreamChanged when the renderer is enabled, so a stream never follows one from before.
    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        super.onPositionReset(positionUs, joining)
        leadIn.onPositionReset()
    }

    /** The last call with the buffer before the base class reads the time it queues it with. */
    override fun shouldDiscardDecoderInputBuffer(buffer: DecoderInputBuffer): Boolean {
        buffer.timeUs = leadIn.queueTimeUs(buffer.timeUs, streamOffsetUs)
        return super.shouldDiscardDecoderInputBuffer(buffer)
    }

    override fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
        super.onQueueInputBuffer(buffer)
        leadIn.onQueued(buffer.timeUs)
        // No frame comes back to be counted: the stall watchdog takes this as the decoder's progress.
        if (isDecodeOnly(buffer)) decoderCounters.skippedInputBufferCount++
    }

    override fun getCodecBufferFlags(buffer: DecoderInputBuffer): Int {
        val flags = super.getCodecBufferFlags(buffer)
        return if (isDecodeOnly(buffer)) flags or MediaCodec.BUFFER_FLAG_DECODE_ONLY else flags
    }

    private fun isDecodeOnly(buffer: DecoderInputBuffer): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            leadIn.contains(buffer.timeUs, streamOffsetUs)

    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format,
    ): Boolean {
        if (codec != null && leadIn.contains(bufferPresentationTimeUs, outputStreamOffsetUs)) {
            skipOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs - outputStreamOffsetUs)
            return true
        }
        return super.processOutputBuffer(
            positionUs,
            elapsedRealtimeUs,
            codec,
            buffer,
            bufferIndex,
            bufferFlags,
            sampleCount,
            bufferPresentationTimeUs,
            isDecodeOnlyBuffer,
            isLastBuffer,
            format,
        )
    }
}

/** [DefaultRenderersFactory] with a [LeadInVideoRenderer] for the video, set up like the default one. */
@UnstableApi
internal class LeadInRenderersFactory(
    context: Context,
    private val clipStartUs: Long,
) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val builder = MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
        out.add(LeadInVideoRenderer(builder, clipStartUs))
    }
}
