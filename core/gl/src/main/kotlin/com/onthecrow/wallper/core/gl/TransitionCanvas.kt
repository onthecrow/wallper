package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import com.onthecrow.wallper.core.model.TransitionEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * What a transition needs of GL ([SlidesRenderer]): a target off the window for each of the two slides, and
 * the pass that blends them onto it. GL thread only.
 */
internal interface TransitionCanvas {
    /**
     * Draws go into the target of [slot] ([LEAVING] or [COMING]) from now on, which is made at [width] x
     * [height] if it isn't yet; nothing else of the state changes.
     */
    fun bindTarget(slot: Int, width: Int, height: Int)

    /** Blends the two targets onto the window, [progress] (0 to 1) of the way from the one leaving. */
    fun composite(progress: Float, width: Int, height: Int)

    /** The transition is over: its targets go, their memory too. */
    fun releaseTargets()

    /** The context is gone, and every GL object with it: nothing is deleted. */
    fun onContextLost()

    /** Deletes everything it made. */
    fun dispose()

    companion object {
        const val LEAVING = 0
        const val COMING = 1
    }
}

/**
 * [TransitionCanvas] of [effect] in GL: two RGBA textures of the window's size behind framebuffers, made when
 * a transition starts and deleted when it ends, and the effect's program, made at its first blend.
 */
internal class GlTransitionCanvas(private val effect: TransitionEffect) : TransitionCanvas {

    private val framebuffers = IntArray(2)
    private val textures = IntArray(2)
    private var targetWidth = 0
    private var targetHeight = 0

    private var program = 0
    private var progressLocation = -1
    private var sizeLocation = -1

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD).position(0) }

    init {
        require(effect != TransitionEffect.NONE) { "No transition has nothing to draw" }
    }

    override fun bindTarget(slot: Int, width: Int, height: Int) {
        if (width != targetWidth || height != targetHeight) releaseTargets()
        if (framebuffers[slot] == 0) makeTarget(slot, width, height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[slot])
        GLES20.glViewport(0, 0, width, height)
    }

    override fun composite(progress: Float, width: Int, height: Int) {
        if (program == 0) makeProgram()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(progressLocation, progress)
        GLES20.glUniform2f(sizeLocation, width.toFloat(), height.toFloat())
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[TransitionCanvas.LEAVING])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[TransitionCanvas.COMING])
        GLES20.glVertexAttribPointer(POSITION_LOCATION, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(POSITION_LOCATION)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(POSITION_LOCATION)
        // The slides draw into these textures next: none stays bound where a renderer might sample it.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "The $effect transition's blend failed: glError 0x${error.toString(16)}" }
    }

    override fun releaseTargets() {
        if (framebuffers.any { it != 0 }) GLES20.glDeleteFramebuffers(2, framebuffers, 0)
        if (textures.any { it != 0 }) GLES20.glDeleteTextures(2, textures, 0)
        forgetTargets()
    }

    override fun onContextLost() {
        forgetTargets()
        program = 0
    }

    override fun dispose() {
        releaseTargets()
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
    }

    private fun forgetTargets() {
        framebuffers.fill(0)
        textures.fill(0)
        targetWidth = 0
        targetHeight = 0
    }

    private fun makeTarget(slot: Int, width: Int, height: Int) {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textures[slot] = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        // Sizes are rarely powers of two: ES 2 samples those only clamped, without mipmaps.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        GLES20.glGenFramebuffers(1, ids, 0)
        framebuffers[slot] = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[slot], 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "A transition's target is incomplete: 0x${status.toString(16)}" }
        targetWidth = width
        targetHeight = height
    }

    private fun makeProgram() {
        val made = ShaderUtils.createProgram(TransitionShaders.VERTEX, TransitionShaders.fragment(effect))
        check(made != 0) { "The $effect transition's shaders don't build" }
        GLES20.glBindAttribLocation(made, POSITION_LOCATION, TransitionShaders.POSITION)
        GLES20.glLinkProgram(made)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(made, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(made)
            error("The $effect transition's program doesn't link")
        }
        GLES20.glUseProgram(made)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(made, TransitionShaders.FROM), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(made, TransitionShaders.TO), 1)
        progressLocation = GLES20.glGetUniformLocation(made, TransitionShaders.PROGRESS)
        sizeLocation = GLES20.glGetUniformLocation(made, TransitionShaders.SIZE)
        program = made
    }

    private companion object {
        const val POSITION_LOCATION = 0

        /** The window's corners, bottom left first, as a triangle strip. */
        val QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    }
}
