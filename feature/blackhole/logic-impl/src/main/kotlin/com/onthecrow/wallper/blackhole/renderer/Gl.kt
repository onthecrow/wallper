package com.onthecrow.wallper.blackhole.renderer

import android.content.res.AssetManager
import android.opengl.GLES30
import com.onthecrow.wallper.blackhole.physics.Vec3

/** A shader that didn't compile or link; the message holds the driver log. */
internal class ShaderException(message: String) : RuntimeException(message)

/**
 * Loads GLSL from `assets/blackhole`, resolves `#include "file"` and puts `#version 300 es` and the
 * [defines] in front.
 */
internal class ShaderLibrary(private val assets: AssetManager) {
    private val cache = HashMap<String, String>()

    fun source(name: String, defines: Map<String, Any> = emptyMap()): String = buildString {
        append("#version 300 es\n")
        defines.forEach { (key, value) -> append("#define ").append(key).append(' ').append(value).append('\n') }
        append(resolve(name, HashSet()))
    }

    private fun resolve(name: String, seen: MutableSet<String>): String {
        if (!seen.add(name)) return ""
        val text = cache.getOrPut(name) { assets.open("blackhole/$name").bufferedReader().use { it.readText() } }
        return text.lineSequence().joinToString("\n") { line ->
            val include = INCLUDE.matchEntire(line.trim())
            if (include != null) resolve(include.groupValues[1], seen) else line
        }
    }

    private companion object {
        val INCLUDE = Regex("#include\\s+\"([^\"]+)\"")
    }
}

/** A linked program with cached uniform locations. GL thread only. */
internal class GlProgram(vertexSource: String, fragmentSource: String, private val name: String) {
    val id: Int
    private val locations = HashMap<String, Int>()

    init {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex)
        GLES30.glAttachShader(id, fragment)
        GLES30.glLinkProgram(id)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            GLES30.glDeleteProgram(id)
            throw ShaderException("$name: link failed: $log")
        }
    }

    fun use() = GLES30.glUseProgram(id)

    private fun location(uniform: String) = locations.getOrPut(uniform) { GLES30.glGetUniformLocation(id, uniform) }

    fun set(uniform: String, value: Float) = GLES30.glUniform1f(location(uniform), value)
    fun set(uniform: String, value: Double) = GLES30.glUniform1f(location(uniform), value.toFloat())
    fun set(uniform: String, x: Float, y: Float) = GLES30.glUniform2f(location(uniform), x, y)
    fun set(uniform: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(location(uniform), x, y, z)
    fun set(uniform: String, v: Vec3) = set(uniform, v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
    fun set(uniform: String, x: Float, y: Float, z: Float, w: Float) = GLES30.glUniform4f(location(uniform), x, y, z, w)
    fun setInt(uniform: String, value: Int) = GLES30.glUniform1i(location(uniform), value)
    fun setUint(uniform: String, value: Int) = GLES30.glUniform1ui(location(uniform), value)
    fun set3v(uniform: String, values: FloatArray) = GLES30.glUniform3fv(location(uniform), values.size / 3, values, 0)
    fun set4v(uniform: String, values: FloatArray) = GLES30.glUniform4fv(location(uniform), values.size / 4, values, 0)
    fun setMat3v(uniform: String, values: FloatArray) =
        GLES30.glUniformMatrix3fv(location(uniform), values.size / 9, false, values, 0)

    /** Binds [texture] of [target] to [unit] and points the sampler [uniform] at it. */
    fun texture(uniform: String, unit: Int, target: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texture)
        setInt(uniform, unit)
    }

    fun release() = GLES30.glDeleteProgram(id)

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw ShaderException("$name: compile failed: $log")
        }
        return shader
    }
}

/** What this context can render into (§6.2). */
internal class GlCaps(extensions: String) {
    val colorBufferFloat = "GL_EXT_color_buffer_float" in extensions
    val colorBufferHalfFloat = "GL_EXT_color_buffer_half_float" in extensions
    val timerQuery = "GL_EXT_disjoint_timer_query" in extensions

    /** HDR targets in order of preference; RGBA8 means no HDR (tonemapping in the lens pass). */
    val targetFormats: List<TargetFormat> = buildList {
        if (colorBufferFloat) add(TargetFormat.R11F_G11F_B10F)
        if (colorBufferFloat || colorBufferHalfFloat) add(TargetFormat.RGBA16F)
        add(TargetFormat.RGBA8)
    }

    val hazeFormat: Int = if (colorBufferFloat) GLES30.GL_R11F_G11F_B10F else GLES30.GL_SRGB8_ALPHA8

    companion object {
        fun current() = GlCaps(GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty())
    }
}

internal enum class TargetFormat(val internalFormat: Int, val isHdr: Boolean) {
    R11F_G11F_B10F(GLES30.GL_R11F_G11F_B10F, true),
    RGBA16F(GLES30.GL_RGBA16F, true),
    RGBA8(GLES30.GL_RGBA8, false),
}

/** A colour texture with its framebuffer. GL thread only. */
internal class RenderTarget private constructor(val width: Int, val height: Int, val texture: Int, val framebuffer: Int) {

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
    }

    /** [bind] for a pass that overwrites every pixel: the old contents are discarded, not loaded. */
    fun bindToOverwrite() {
        bind()
        Gl.discardColor(defaultFramebuffer = false)
    }

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    companion object {
        /** Null if [format] isn't renderable here. */
        fun create(width: Int, height: Int, format: TargetFormat): RenderTarget? {
            val texture = Gl.genTexture()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, format.internalFormat, width, height)
            Gl.clampLinear(GLES30.GL_TEXTURE_2D)
            val framebuffer = Gl.genFramebuffer()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
            val complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            if (!complete) {
                GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
                GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
                return null
            }
            return RenderTarget(width, height, texture, framebuffer)
        }
    }
}

internal object Gl {
    fun genTexture(): Int = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]

    fun genFramebuffer(): Int = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]

    fun deleteTexture(texture: Int) {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    fun clampLinear(target: Int) {
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    /**
     * The bound framebuffer's colour is about to be overwritten whole. Without this a tile-based GPU loads
     * the old contents into tile memory first (a fullscreen triangle doesn't tell the driver otherwise).
     */
    fun discardColor(defaultFramebuffer: Boolean) =
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, if (defaultFramebuffer) WINDOW_COLOR else ATTACHED_COLOR, 0)

    private val WINDOW_COLOR = intArrayOf(GLES30.GL_COLOR)
    private val ATTACHED_COLOR = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0)

    /** Draws the fullscreen triangle of `fullscreen.vert`. */
    fun drawFullscreen() = GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

    fun mipLevels(size: Int): Int = 32 - Integer.numberOfLeadingZeros(size.coerceAtLeast(1))
}

/** GPU frame time from `EXT_disjoint_timer_query`; results arrive a few frames late. GL thread only. */
internal class GpuTimer {
    private val queries = IntArray(RING)
    private val pending = BooleanArray(RING)
    private var index = 0
    private val scratch = IntArray(1) // polled every frame: no new arrays

    init {
        GLES30.glGenQueries(RING, queries, 0)
    }

    fun begin() {
        if (pending[index]) return
        GLES30.glBeginQuery(GL_TIME_ELAPSED_EXT, queries[index])
    }

    fun end() {
        if (pending[index]) return
        GLES30.glEndQuery(GL_TIME_ELAPSED_EXT)
        pending[index] = true
        index = (index + 1) % RING
    }

    /** The oldest finished measurement in ms, or null; measurements across a disjoint event are dropped. */
    fun poll(): Double? {
        val oldest = index // the next slot to reuse is the oldest pending one
        if (!pending[oldest]) return null
        GLES30.glGetQueryObjectuiv(queries[oldest], GLES30.GL_QUERY_RESULT_AVAILABLE, scratch, 0)
        if (scratch[0] == 0) return null
        GLES30.glGetQueryObjectuiv(queries[oldest], GLES30.GL_QUERY_RESULT, scratch, 0)
        val nanos = scratch[0].toLong() and 0xFFFFFFFFL
        pending[oldest] = false
        GLES30.glGetIntegerv(GL_GPU_DISJOINT_EXT, scratch, 0)
        if (scratch[0] != 0) return null
        return nanos / 1e6
    }

    fun release() = GLES30.glDeleteQueries(RING, queries, 0)

    private companion object {
        const val RING = 4
        const val GL_TIME_ELAPSED_EXT = 0x88BF
        const val GL_GPU_DISJOINT_EXT = 0x8FBB
    }
}
