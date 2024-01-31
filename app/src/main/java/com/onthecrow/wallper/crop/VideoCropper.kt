package com.onthecrow.wallper.crop

import android.graphics.Rect
import com.onthecrow.wallper.util.FileUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Qualifier


abstract class VideoCropper {

    protected var fallbackCropper: VideoCropper? = null

    fun crop(
        rectToCropFor: Rect,
        inputFilePath: String,
        outputFilePath: String,
    ): Flow<VideoCroppingStatus> {
        FileUtils.deleteFileIfExists(outputFilePath)
        return cropInternal(
            rectToCropFor,
            inputFilePath,
            outputFilePath,
        ).map {
            if (it is VideoCroppingStatus.Error) {
                fallbackCropper?.crop(rectToCropFor, inputFilePath, outputFilePath)
            } else {
                null
            } ?: flowOf(it)
        }.flattenConcat()
    }

    fun setFallbackCropper(videoCropper: VideoCropper): VideoCropper {
        fallbackCropper = videoCropper
        return this
    }

    protected abstract fun cropInternal(
        rectToCropFor: Rect,
        inputFilePath: String,
        outputFilePath: String,
    ): Flow<VideoCroppingStatus>
}

@Qualifier
internal annotation class MainVideoCropper

@Qualifier
internal annotation class FallbackVideoCropper