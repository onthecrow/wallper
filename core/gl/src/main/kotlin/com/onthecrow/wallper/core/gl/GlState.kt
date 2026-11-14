package com.onthecrow.wallper.core.gl

import android.opengl.GLES20

/** The state of a GL context that several renderers take turns in. GL thread only. */
internal object GlState {

    /**
     * Puts the context back to how a new one starts, as far as the wallpapers' renderers change it: each
     * was written to have a context to itself and leaves its own state behind. Covers what they touch today
     * (no vertex array objects, no pixel buffers); a renderer that binds more must be reset here too.
     *
     * Pending errors are dropped as well: a renderer that checks `glGetError` would take another one's for
     * its own and throw.
     */
    fun reset(width: Int, height: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        repeat(VERTEX_ATTRIBUTES) { GLES20.glDisableVertexAttribArray(it) }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DITHER)
        GLES20.glColorMask(true, true, true, true)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, DEFAULT_UNPACK_ALIGNMENT)
        repeat(MAX_PENDING_ERRORS) { if (GLES20.glGetError() == GLES20.GL_NO_ERROR) return }
    }

    /** What ES 2.0 promises at least, and more than the renderers use. */
    private const val VERTEX_ATTRIBUTES = 8
    private const val DEFAULT_UNPACK_ALIGNMENT = 4

    /** A driver keeps a flag per kind of error; a lost context answers with an error for ever. */
    private const val MAX_PENDING_ERRORS = 8
}
