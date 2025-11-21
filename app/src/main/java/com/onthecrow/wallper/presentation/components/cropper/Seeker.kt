package com.onthecrow.wallper.presentation.components.cropper

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun Seeker(
    uri: String,
    seedPosition: Float,
) {
    val context = LocalContext.current
    var seeker by remember { mutableStateOf(VideoFrameDecoder(context, uri.toUri())) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(seedPosition) {
        if (!initialized) return@LaunchedEffect
        val position = (seedPosition * seeker.durationUs).toLong()
        seeker.showFrameAt(position)
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val surface = Surface(surfaceTexture)
                        // Инициализируем декодер
                        seeker.setSurface(surface)
                        seeker.prepare()
                        initialized = true
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        seeker.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
    )
}

class VideoFrameDecoder(
    private val context: Context,
    private val uri: Uri,
) {

    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private var videoTrackIndex: Int = -1
    var durationUs: Long = 0L

//    private val decodeThread = HandlerThread("frame-decoder").apply { start() }
//    private val handler = Handler(decodeThread.looper)

    @Volatile
    private var cancelled = false

    private var outputSurface: Surface? = null

    fun setSurface(surface: Surface) {
        outputSurface = surface
    }

    fun prepare() {
        extractor.setDataSource(context, uri, null)

        // Находим видео-дорожку
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                extractor.selectTrack(videoTrackIndex)
                durationUs = format.getLong(MediaFormat.KEY_DURATION)
                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(format, outputSurface, null, 0)
                    start()
                }
                break
            }
        }

        if (videoTrackIndex == -1) {
            throw IllegalStateException("No video track found")
        }
    }

    fun release() {
//        handler.post {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Throwable) {
            }

            try {
                extractor.release()
            } catch (_: Throwable) {
            }
//        }
//        decodeThread.quitSafely()
    }

    private var job: Job? = null
    /**
     * Показать кадр, ближайший к timeUs (с точностью до кадра).
     * Старый запрос можно пометить как отменённый.
     */
    suspend fun showFrameAt(timeUs: Long) {
        cancelled = true  // отменяем предыдущий цикл
        cancelled = false

        job?.cancelAndJoin()
        job = MainScope().launch(Dispatchers.IO) {
            if (codec == null) {
                return@launch
            }
            runCatching {
                decodeSingleFrame(timeUs, this)
            }.onFailure { Timber.e(it) }
        }
    }

    private fun decodeSingleFrame(targetTimeUs: Long, scope: CoroutineScope) {
        val codec = codec ?: return

        // Сбрасываем состояние декодера и extractor для нового seek
        codec.flush()
        extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var rendered = false

        val TIMEOUT_US = 10_000L

        while (!outputDone && scope.isActive) {
            // Кормим входные буферы
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        val sampleTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(
                            inIndex,
                            0,
                            sampleSize,
                            sampleTimeUs,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            // Читаем выходные буферы
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // нет готовых данных, просто продолжаем
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // можно игнорировать
                }

                outIndex >= 0 -> {
                    val isTargetFrame =
                        bufferInfo.presentationTimeUs >= targetTimeUs ||
                                (inputDone && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)

                    // Рисуем только кадр, который достиг нужного времени
                    codec.releaseOutputBuffer(outIndex, isTargetFrame)

                    if (isTargetFrame) {
                        // кадр отправлен на Surface
                        rendered = true
                        outputDone = true
                    }
                }
            }
        }
    }
}