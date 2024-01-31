package com.onthecrow.wallper.crop

import android.graphics.Rect
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Log
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.concurrent.TimeUnit

class FfmpegVideoCropperImpl : VideoCropper() {

    private val regexDuration = Regex("\\d{2}:\\d{2}:\\d{2}.\\d{2}")
    private val regexFps = Regex("(\\d{2})\\sfps")
    private var totalFrames = -1L
    private var duration = -1L
    private var fps = -1f

    override fun cropInternal(
        rectToCropFor: Rect,
        inputFilePath: String,
        outputFilePath: String
    ): Flow<VideoCroppingStatus> {
        return callbackFlow {
            send(VideoCroppingStatus.InProgress())
            val command = "-i $inputFilePath -c:v libx264 -filter:v \"crop=" +
                    "${rectToCropFor.width()}:${rectToCropFor.height()}:${rectToCropFor.left}" +
                    ":${rectToCropFor.top}\" $outputFilePath"
            Timber.d("ffmpeg command: $command")
            val session = FFmpegKit.executeAsync(
                command,
                { session ->
                    processFFmpegSession(
                        session,
                        onSuccess = { trySend(VideoCroppingStatus.Success) },
                        onError = { trySend(VideoCroppingStatus.Error()) }
                    )
                },
                ::processFFmpegLog,
                { statistics -> trySend(processFFmpegStatistics(statistics)) },
            )
            awaitClose { FFmpegKit.cancel(session.sessionId) }
        }
    }

    private fun processFFmpegSession(
        session: FFmpegSession,
        onSuccess: () -> Unit,
        onError: () -> Unit,
    ) {
        when {
            ReturnCode.isSuccess(session.returnCode) -> { onSuccess() }
            // TODO implement 2 different errors for cancel and error
            ReturnCode.isCancel(session.returnCode) -> { onError() }
            else -> { onError() }
        }
    }

    private fun processFFmpegStatistics(statistics: Statistics): VideoCroppingStatus.InProgress {
        if (duration != -1L && fps != -1f) {
            val currentTime = statistics.time
            val currentFrameNumber = statistics.videoFrameNumber
            val framesToGo = totalFrames - currentFrameNumber
            val remainingTime = (currentFrameNumber / currentTime) * framesToGo
            val elapsedTime = currentTime / 1000
            return VideoCroppingStatus.InProgress(
                progress = (currentFrameNumber / totalFrames * 100).toInt(),
                remainingTime = remainingTime.toInt(),
                elapsedTime = elapsedTime.toInt(),
            )
        }
        return VideoCroppingStatus.InProgress()
    }

    private fun processFFmpegLog(log: Log) {
        if (duration == -1L) {
            regexDuration.find(log.message)?.let { durationFromLog ->
                processFFmpegDuration(durationFromLog.value)
            }
        }
        if (fps == -1f) {
            regexFps.find(log.message)?.let { fpsFromLog ->
                fps = fpsFromLog.groupValues.getOrNull(1)?.toFloatOrNull() ?: return
            }
        }
        if (fps != -1f && duration != -1L) {
            totalFrames = ((duration.toFloat() / 1000) * fps).toLong()
        }
    }

    private fun processFFmpegDuration(ffmpegDuration: String) {
        ffmpegDuration.split(':').run {
            val hours = get(0).toLongOrNull() ?: return
            val minutes = get(1).toLongOrNull() ?: return
            val (seconds, millis) = get(2).split('.').let { secondsAndMillis ->
                (secondsAndMillis.firstOrNull()?.toLongOrNull() ?: return) to
                        ("0.${secondsAndMillis.lastOrNull() ?: return}".toFloatOrNull()
                            ?: return) * 1000
            }
            duration = millis.toLong() + TimeUnit.SECONDS.toMillis(seconds) +
                    TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.HOURS.toMillis(hours)
        }
    }
}