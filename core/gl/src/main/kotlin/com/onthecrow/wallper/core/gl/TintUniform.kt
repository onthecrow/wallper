package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import com.onthecrow.wallper.core.model.Tint

/**
 * A wallpaper's [Tint] in a fragment shader: one multiplication on the way out, no blending and no pass of
 * its own, so it leaves no GL state behind for a renderer sharing the context. The shader declares
 * [DECLARATION] and writes [APPLIED] of its `color`; the uniform is all ones without a tint, which leaves the
 * picture as it is.
 */
internal object TintUniform {
    const val NAME = "uTint"

    /** What each of the picture's channels is multiplied by. */
    const val DECLARATION = "uniform vec3 $NAME;"

    /** A `vec4 color` seen through the tint, its own alpha kept. */
    const val APPLIED = "vec4(color.rgb * $NAME, color.a)"

    /** Sets the uniform at [location] of the program in use. GL thread only. */
    fun set(location: Int, tint: Tint?) {
        if (tint == null) {
            GLES20.glUniform3f(location, 1f, 1f, 1f)
        } else {
            GLES20.glUniform3f(location, tint.redFactor, tint.greenFactor, tint.blueFactor)
        }
    }
}
