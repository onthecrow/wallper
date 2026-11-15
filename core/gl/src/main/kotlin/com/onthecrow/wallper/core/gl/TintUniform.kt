package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import com.onthecrow.wallper.core.model.Tint

/**
 * A wallpaper's [Tint] in a fragment shader: one `mix` on the way out, no blending and no pass of its own, so
 * it leaves no GL state behind for a renderer sharing the context. The shader declares [DECLARATION] and writes
 * [MIX] of its `color`; the uniform is all zeros without a tint, which leaves the picture as it is.
 */
internal object TintUniform {
    const val NAME = "uTint"

    /** rgb is the colour, a how much of it shows. */
    const val DECLARATION = "uniform vec4 $NAME;"

    /** A `vec4 color` with the tint over it, its own alpha kept. */
    const val MIX = "vec4(mix(color.rgb, $NAME.rgb, $NAME.a), color.a)"

    /** Sets the uniform at [location] of the program in use. GL thread only. */
    fun set(location: Int, tint: Tint?) {
        if (tint == null) {
            GLES20.glUniform4f(location, 0f, 0f, 0f, 0f)
        } else {
            GLES20.glUniform4f(location, tint.red, tint.green, tint.blue, tint.alpha)
        }
    }
}
