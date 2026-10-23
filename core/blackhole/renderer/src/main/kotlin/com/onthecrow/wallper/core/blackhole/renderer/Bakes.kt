package com.onthecrow.wallper.core.blackhole.renderer

import android.opengl.GLES30
import com.onthecrow.wallper.core.blackhole.physics.DiskParams
import com.onthecrow.wallper.core.blackhole.physics.HazeParams
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** The trajectory tables on the GPU (§5.3): u(t, φ) as an R16F 2D array, φ_end as R32F. GL thread only. */
internal class LensTextures(val tables: LensTables) {
    val trajectories: Int = Gl.genTexture()
    val ends: Int = Gl.genTexture()

    init {
        val key = tables.key
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, trajectories)
        // R16F from GL_FLOAT is a valid upload in ES 3.0, and R16F filters linearly everywhere (R32F doesn't).
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R16F, key.nAlpha, key.nPhi, 2, 0,
            GLES30.GL_RED, GLES30.GL_FLOAT, tables.trajectories.toBuffer(),
        )
        Gl.clampLinear(GLES30.GL_TEXTURE_2D_ARRAY)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ends)
        // φ_end reaches ~25 rad, where fp16 steps by 0.01 rad: R32F, read with texelFetch.
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R32F, key.nAlpha, 2, 0,
            GLES30.GL_RED, GLES30.GL_FLOAT, tables.phiEnd.toBuffer(),
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
    }

    fun release() {
        Gl.deleteTexture(trajectories)
        Gl.deleteTexture(ends)
    }

    private fun FloatArray.toBuffer() = ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(this).position(0)
}

/** The polar disk detail texture (§8.2), baked by a shader into RG8 with mipmaps. GL thread only. */
internal class DiskDetailTexture(program: GlProgram, val width: Int, val height: Int, disk: DiskParams, anisotropy: Float) {
    val texture: Int = Gl.genTexture()

    init {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, Gl.mipLevels(maxOf(width, height)), GLES30.GL_RG8, width, height)
        val framebuffer = Gl.genFramebuffer()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
        GLES30.glViewport(0, 0, width, height)
        program.use()
        program.set("uSize", width.toFloat(), height.toFloat())
        program.setUint("uSeed", disk.detailSeed)
        program.set("uStretch", disk.fibreStretch)
        program.set("uClumps", disk.clumps)
        Gl.drawFullscreen()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // The disk is seen almost edge-on: anisotropic filtering keeps it crisp where the hardware has it.
        if (anisotropy > 1f) GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy)
    }

    fun release() = Gl.deleteTexture(texture)

    companion object {
        const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
    }
}

/** The diffuse sky cubemap (§9.4), baked face by face, with mipmaps. GL thread only. */
internal class HazeCubemap(program: GlProgram, val faceSize: Int, format: Int, haze: HazeParams) {
    val texture: Int = Gl.genTexture()

    init {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texture)
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_CUBE_MAP, Gl.mipLevels(faceSize), format, faceSize, faceSize)
        val framebuffer = Gl.genFramebuffer()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, faceSize, faceSize)
        program.use()
        val tilt = Math.toRadians(haze.milkyWayTiltDeg)
        val turn = 0.7
        program.set("uFaceSize", faceSize.toFloat())
        program.setUint("uSeed", haze.seed)
        program.set("uMilkyWayNormal", (sin(tilt) * sin(turn)).toFloat(), cos(tilt).toFloat(), (sin(tilt) * cos(turn)).toFloat())
        program.set("uMilkyWayWidth", haze.milkyWayWidth)
        program.set("uMilkyWayBrightness", haze.milkyWayBrightness)
        program.set("uDust", haze.dust)
        program.set("uNebulae", haze.nebulae)
        program.set("uGlow", haze.glow)
        for (face in 0 until 6) {
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, texture, 0,
            )
            program.setInt("uFace", face)
            Gl.drawFullscreen()
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, texture)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    fun release() = Gl.deleteTexture(texture)
}

/** Dual filtering bloom chain (§6.2): one target per level, each half the previous. GL thread only. */
internal class BloomChain(sceneWidth: Int, sceneHeight: Int, levels: Int, format: TargetFormat) {
    val levels: List<RenderTarget> = buildList {
        var w = sceneWidth
        var h = sceneHeight
        repeat(levels) {
            w = (w / 2).coerceAtLeast(1)
            h = (h / 2).coerceAtLeast(1)
            RenderTarget.create(w, h, format)?.let(::add)
        }
    }

    /** Bright pass and downsample all the way down, then upsample adding onto every level. Result in [levels][0]. */
    fun run(scene: RenderTarget, down: GlProgram, up: GlProgram, threshold: Float, knee: Float) {
        if (levels.isEmpty()) return
        down.use()
        down.set("uThreshold", threshold)
        down.set("uKnee", knee)
        var source = scene
        levels.forEachIndexed { i, target ->
            target.bind()
            down.texture("uSource", 0, GLES30.GL_TEXTURE_2D, source.texture)
            down.set("uTexel", 1f / source.width, 1f / source.height)
            down.setInt("uPrefilter", if (i == 0) 1 else 0)
            Gl.drawFullscreen()
            source = target
        }
        up.use()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        for (i in levels.size - 2 downTo 0) {
            val smaller = levels[i + 1]
            levels[i].bind()
            up.texture("uSource", 0, GLES30.GL_TEXTURE_2D, smaller.texture)
            up.set("uTexel", 1f / smaller.width, 1f / smaller.height)
            Gl.drawFullscreen()
        }
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() = levels.forEach(RenderTarget::release)
}
