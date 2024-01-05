package com.onthecrow.wallper.util

import android.opengl.GLES20
import timber.log.Timber

object GLErrorUtils {
    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Timber.e("$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }
}