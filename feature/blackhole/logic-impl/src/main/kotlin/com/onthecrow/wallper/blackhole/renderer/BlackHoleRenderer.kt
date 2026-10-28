package com.onthecrow.wallper.blackhole.renderer

import android.content.res.AssetManager
import android.opengl.GLES30
import com.onthecrow.wallper.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.blackhole.physics.FrameState
import com.onthecrow.wallper.blackhole.physics.LensMath
import com.onthecrow.wallper.blackhole.physics.LensTables
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.physics.StarLayer
import com.onthecrow.wallper.blackhole.physics.StarMath
import com.onthecrow.wallper.blackhole.physics.Tonemapper
import com.onthecrow.wallper.blackhole.physics.effectiveRenderScale
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The black hole on the current GLES 3.0 context (BLACKHOLE_TZ.md §6): Lens → Bloom → Composite.
 * GPU resources are (re)created lazily on the next [render] after anything they depend on changes.
 *
 * Every method runs on the thread that owns the context. Shared by the wallpaper, the debug screen
 * and the list preview.
 */
internal class BlackHoleRenderer(assets: AssetManager) : BlackHoleFrameRenderer {

    /** GPU time of a whole frame, when the driver can measure it. Null stops the measuring. */
    override var onGpuTimeMs: ((Double) -> Unit)? = null

    /** Once per context when the driver can't measure it: [onGpuTimeMs] will never come. */
    var onNoGpuTimer: (() -> Unit)? = null

    /** Shaders failed to build on this device; [render] only clears. */
    override var failure: Throwable? = null
        private set

    /** Fade the picture in once the tables arrive; off for still previews. */
    var fadeIn: Boolean = true

    /**
     * The tables on the GPU died with the context and no CPU copy is kept: send them again with [setTables].
     * Called on the GL thread.
     */
    var onTablesLost: (() -> Unit)? = null

    private val shaders = ShaderLibrary(assets)

    private var params = BlackHoleParams()
    private var quality = QualityLevel.MEDIUM
    private var debug = DebugOptions()
    private var pendingTables: LensTables? = null

    private var caps: GlCaps? = null
    private var programs: Programs? = null
    private var lens: LensTextures? = null
    private var diskDetail: DiskDetailTexture? = null
    private var haze: HazeCubemap? = null
    private var scene: RenderTarget? = null
    private var debugTargets: Pair<RenderTarget, RenderTarget>? = null
    private var bloom: BloomChain? = null
    private var gpuTimer: GpuTimer? = null
    private var format = TargetFormat.RGBA8

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var fadeStartNanos = Long.MIN_VALUE
    private var frameCounter = 0

    private var starRotations = FloatArray(0)
    private var starRotationSeed = Int.MIN_VALUE

    // Uniform data that changes only with the params or the quality: rebuilt then, not on every frame.
    private var uniformsParams: BlackHoleParams? = null
    private var uniformsQuality: QualityLevel? = null
    private val rampPositions = FloatArray(4)
    private val rampColors = FloatArray(12)
    private var starLayers: List<StarLayer> = emptyList()
    private var starLayerData = FloatArray(0)
    private val starTint = FloatArray(3)
    private var starAverages = FloatArray(0)

    override fun setParams(params: BlackHoleParams) {
        val old = this.params
        this.params = params
        if (old.disk.detailSeed != params.disk.detailSeed || old.disk.fibreStretch != params.disk.fibreStretch ||
            old.disk.clumps != params.disk.clumps
        ) {
            diskDetail?.release()
            diskDetail = null
        }
        if (old.haze != params.haze) {
            haze?.release()
            haze = null
        }
        if (quality.effectiveRenderScale(old.frames) != quality.effectiveRenderScale(params.frames)) releaseTargets()
        if (old.programDefines() != params.programDefines()) {
            programs?.release()
            programs = null
        }
    }

    /**
     * Quality changes the shaders, the render scale and the baked textures; the tables come separately.
     * Only what the new level changes is rebuilt: a heat step between High and Medium, on a hot phone,
     * keeps the programs and the bakes and only makes new targets.
     */
    override fun setQuality(level: QualityLevel) {
        if (level == quality) return
        val old = quality
        quality = level
        if (old.diskCrossings != level.diskCrossings || old.starLayers != level.starLayers) {
            programs?.release()
            programs = null
        }
        if (old.hazeFace != level.hazeFace) {
            haze?.release()
            haze = null
        }
        if (old.diskDetailWidth != level.diskDetailWidth || old.diskDetailHeight != level.diskDetailHeight) {
            diskDetail?.release()
            diskDetail = null
        }
        val frames = params.frames
        if (old.effectiveRenderScale(frames) != level.effectiveRenderScale(frames) || old.bloomLevels != level.bloomLevels) {
            releaseTargets()
        }
        starRotationSeed = Int.MIN_VALUE // one rotation per star layer
    }

    /** Held until the next frame uploads them, then dropped. */
    override fun setTables(tables: LensTables) {
        pendingTables = tables
    }

    override fun setDebug(options: DebugOptions) {
        debug = options
    }

    /** Size of the output; the lens pass runs at a fraction of it. */
    override fun onSurfaceSize(width: Int, height: Int) {
        if (width == surfaceWidth && height == surfaceHeight) return
        surfaceWidth = width
        surfaceHeight = height
        releaseTargets()
    }

    /** The context is gone: forget every GPU name without deleting it, they died with the context. */
    override fun onContextLost() {
        programs = null
        val tablesLost = lens != null && pendingTables == null
        lens = null
        diskDetail = null
        haze = null
        scene = null
        debugTargets = null
        bloom = null
        gpuTimer = null
        caps = null
        if (tablesLost) onTablesLost?.invoke()
    }

    /** Deletes every GPU object, the tables too: [setTables] again before drawing in a new context. */
    fun release() {
        releaseGpu()
        lens?.release()
        lens = null
    }

    /**
     * Draws [frame] into [outputFramebuffer] (0 is the window). Returns false while the tables aren't
     * ready or the shaders failed: the frame is black then.
     */
    override fun render(frame: FrameState, nowNanos: Long, outputFramebuffer: Int): Boolean {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return false
        if (failure == null) {
            try {
                ensureResources()
            } catch (e: ShaderException) {
                failure = e
            }
        }
        val programs = programs
        val lens = lens
        val scene = scene
        if (failure != null || programs == null || lens == null || scene == null) {
            clearOutput(outputFramebuffer)
            return false
        }
        if (fadeStartNanos == Long.MIN_VALUE) fadeStartNanos = nowNanos
        frameCounter = (frameCounter + 1) % NOISE_FRAMES

        val timer = gpuTimer
        timer?.poll()?.let { onGpuTimeMs?.invoke(it) }
        timer?.begin()

        drawLens(programs, lens, scene, frame)
        val post = params.post
        val bloom = bloom.takeIf { post.bloomEnabled && debug.lens != LensMode.DIFF }
        val threshold = if (format.isHdr) post.bloomThreshold.toFloat() else minOf(post.bloomThreshold.toFloat(), LDR_THRESHOLD)
        bloom?.run(scene, programs.bloomDown, programs.bloomUp, threshold, post.bloomKnee.toFloat())
        drawComposite(programs, scene, bloom, nowNanos, outputFramebuffer)

        timer?.end()
        return true
    }

    // ---------- passes ----------

    private fun drawLens(programs: Programs, lens: LensTextures, scene: RenderTarget, frame: FrameState) {
        when (debug.lens) {
            LensMode.TABLE -> {
                scene.bindToOverwrite()
                lensPass(programs.lens, lens, scene, frame)
            }
            LensMode.REFERENCE -> {
                scene.bindToOverwrite()
                lensPass(programs.reference, lens, scene, frame)
            }
            LensMode.SPLIT -> {
                scene.bindToOverwrite() // both halves together cover it
                GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
                GLES30.glScissor(0, 0, scene.width / 2, scene.height)
                lensPass(programs.lens, lens, scene, frame)
                GLES30.glScissor(scene.width / 2, 0, scene.width - scene.width / 2, scene.height)
                lensPass(programs.reference, lens, scene, frame)
                GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
            }
            LensMode.DIFF -> {
                val (a, b) = debugTargets ?: createDebugTargets(scene).also { debugTargets = it }
                a.bindToOverwrite()
                lensPass(programs.lens, lens, a, frame)
                b.bindToOverwrite()
                lensPass(programs.reference, lens, b, frame)
                scene.bindToOverwrite()
                programs.diff.use()
                programs.diff.texture("uA", 0, GLES30.GL_TEXTURE_2D, a.texture)
                programs.diff.texture("uB", 1, GLES30.GL_TEXTURE_2D, b.texture)
                programs.diff.set("uScale", DIFF_SCALE)
                Gl.drawFullscreen()
            }
        }
    }

    private fun lensPass(program: GlProgram, lens: LensTextures, target: RenderTarget, frame: FrameState) {
        program.use()
        val key = lens.key
        val rc = key.cameraDistance
        val pose = frame.pose
        // The tables fix r_c: keep the camera on their sphere even if the params moved on.
        val position = pose.position * (rc / pose.position.length)
        program.set("uCamPos", position)
        program.set("uCamRight", pose.right)
        program.set("uCamUp", pose.up)
        program.set("uCamFwd", pose.forward)
        val aspect = target.width.toFloat() / target.height
        program.set("uTanHalfFov", (frame.tanHalfFovY * aspect).toFloat(), frame.tanHalfFovY.toFloat())
        // The recoil is in half screen heights on both axes: x shrinks to NDC by the aspect.
        program.set("uPrincipal", (frame.principalX + frame.recoilX / aspect).toFloat(), (frame.principalY + frame.recoilY).toFloat())
        program.set("uRc", rc)
        val alphaC = LensMath.alphaC(rc)
        program.set("uAlphaC", alphaC)
        program.set("uLensStrength", params.lens.strength)
        val skyLens = params.lens
        if (skyLens.fadeEnd.isInfinite()) {
            program.set("uLensFade", NO_FADE, NO_FADE + 1f)
        } else {
            program.set("uLensFade", (skyLens.fadeStart * alphaC).toFloat(), (skyLens.fadeEnd * alphaC).toFloat())
        }
        program.set("uExposure", params.post.exposure)
        program.setInt("uTonemapper", params.post.tonemapper.index)
        program.setInt("uDebugView", debug.view.ordinal)
        program.set("uK", key.k)
        program.set("uExpKm1", exp(key.k) - 1)
        program.set("uTabSize", key.nAlpha.toFloat(), key.nPhi.toFloat())
        program.texture("uTrajU", UNIT_TRAJECTORIES, GLES30.GL_TEXTURE_2D_ARRAY, lens.trajectories)
        program.texture("uEnd", UNIT_ENDS, GLES30.GL_TEXTURE_2D, lens.ends)

        val disk = params.disk
        program.texture("uDiskDetail", UNIT_DISK, GLES30.GL_TEXTURE_2D, diskDetail?.texture ?: 0)
        program.set("uDiskEnabled", if (disk.enabled) 1f else 0f)
        program.set("uDiskInner", disk.innerRadius)
        program.set("uDiskOuter", disk.outerRadius)
        program.set("uDiskInnerWidth", disk.innerWidth)
        program.set("uDiskOuterFade", disk.outerFade)
        program.set("uDiskPower", disk.falloffPower)
        program.set("uSpin", frame.diskSpin)
        program.set("uSense", disk.sense.toFloat())
        program.set("uPhaseA", frame.diskPhaseA)
        program.set("uPhaseB", frame.diskPhaseB)
        program.set("uFlowMix", frame.diskFlowMix)
        program.set("uDiskOpacity", disk.opacity)
        program.set("uDiskOpacityFalloff", if (disk.opacityFalloffEnabled) disk.opacityFalloffPower else 0.0)
        program.set("uDiskBrightness", disk.brightness)
        refreshUniformData()
        program.set("uRampPos", rampPositions[0], rampPositions[1], rampPositions[2], rampPositions[3])
        program.set3v("uRampColor", rampColors)
        program.set("uDopplerStrength", if (params.doppler.enabled) params.doppler.strength else 0.0)

        val stars = params.stars
        val sigma = StarMath.sigmaRad(stars.sizePx, frame.tanHalfFovY, surfaceHeight)
        program.texture("uHaze", UNIT_HAZE, GLES30.GL_TEXTURE_CUBE_MAP, haze?.texture ?: 0)
        program.set("uHazeEnabled", if (params.haze.enabled) 1f else 0f)
        program.set("uStarsEnabled", if (stars.enabled) 1f else 0f)
        program.setMat3v("uStarRot", starRotations(stars.seed))
        // σ and the average depend on the star size in pixels, so on the zoom: per frame, into the same arrays.
        starAverages.fill(0f)
        starLayers.forEachIndexed { index, layer ->
            val layerSigma = StarMath.layerSigmaRad(layer, sigma)
            starLayerData[4 * index + 3] = layerSigma.toFloat()
            val radiance = StarMath.averageRadiance(layer, layerSigma, stars.magPower)
            for (channel in 0 until 3) starAverages[3 * index + channel] = (radiance * starTint[channel]).toFloat()
        }
        program.set4v("uStarLayer", starLayerData)
        program.set3v("uStarAvg", starAverages)
        program.set("uStarSigma", sigma)
        program.set("uStarWidening", stars.footprintWidening)
        program.set("uStarFade", stars.fadeStart.toFloat(), stars.fadeEnd.toFloat())
        program.set("uMagPower", stars.magPower)
        program.set("uStarWarmth", stars.warmth)
        program.set("uStarBrightness", stars.brightness)
        program.setUint("uStarSeed", stars.seed)
        program.set("uFpMax", FP_MAX)
        Gl.drawFullscreen()
    }

    private fun refreshUniformData() {
        if (uniformsParams === params && uniformsQuality == quality) return
        uniformsParams = params
        uniformsQuality = quality

        val ramp = params.disk.ramp
        for (i in 0 until 4) {
            val stop = ramp[minOf(i, ramp.lastIndex)] // fewer stops repeat the last one
            rampPositions[i] = stop.position
            rampColors[3 * i] = stop.multiplier * stop.red
            rampColors[3 * i + 1] = stop.multiplier * stop.green
            rampColors[3 * i + 2] = stop.multiplier * stop.blue
        }

        val stars = params.stars
        starLayers = stars.layers.take(quality.starLayers).map { StarMath.effectiveLayer(it, stars.countScale) }
        starLayerData = FloatArray(4 * quality.starLayers) { i ->
            val layer = starLayers.getOrNull(i / 4)
            when {
                // A missing layer stays empty; one cell, never zero cells: 1 / cells must not be infinite. Nor a
                // zero σ: σ² / σ²_eff would be 0 / 0 without widening.
                layer == null -> if (i % 4 == 0 || i % 4 == 3) 1f else 0f
                i % 4 == 0 -> layer.cells.coerceAtLeast(1).toFloat()
                i % 4 == 1 -> layer.density.toFloat()
                i % 4 == 2 -> layer.flux.toFloat()
                else -> 0f // σ, per frame in lensPass
            }
        }
        for (channel in 0 until 3) {
            val cool = STAR_TINT_COOL[channel]
            starTint[channel] = cool + (STAR_TINT_WARM[channel] - cool) * stars.warmth.toFloat()
        }
        starAverages = FloatArray(3 * quality.starLayers)
    }

    private fun drawComposite(
        programs: Programs,
        scene: RenderTarget,
        bloom: BloomChain?,
        nowNanos: Long,
        outputFramebuffer: Int,
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFramebuffer)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        Gl.discardColor(defaultFramebuffer = outputFramebuffer == 0)
        val program = programs.composite
        program.use()
        program.texture("uScene", 0, GLES30.GL_TEXTURE_2D, scene.texture)
        program.set("uSceneSize", scene.width.toFloat(), scene.height.toFloat())
        program.set("uSharpUpscale", if (params.post.sharpUpscale) 1f else 0f)
        program.set("uOneToOne", if (scene.width == surfaceWidth && scene.height == surfaceHeight) 1f else 0f)
        val bloomTexture = bloom?.levels?.firstOrNull()?.texture
        program.texture("uBloom", 1, GLES30.GL_TEXTURE_2D, bloomTexture ?: scene.texture)
        program.set("uBloomIntensity", if (bloomTexture != null) params.post.bloomIntensity else 0.0)
        program.set("uExposure", params.post.exposure)
        program.setInt("uTonemapper", params.post.tonemapper.index)
        program.set("uWarmth", params.post.warmth)
        program.set("uGrain", params.post.grain)
        val fade = if (fadeIn) ((nowNanos - fadeStartNanos) / FADE_NANOS.toDouble()).coerceIn(0.0, 1.0) else 1.0
        program.set("uFade", fade * fade * (3 - 2 * fade))
        program.set("uNoiseFrame", frameCounter.toFloat())
        Gl.drawFullscreen()
    }

    private fun clearOutput(framebuffer: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    // ---------- resources ----------

    private fun ensureResources() {
        val caps = caps ?: GlCaps.current().also {
            caps = it
            if (!it.timerQuery) onNoGpuTimer?.invoke()
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DITHER)
        if (scene == null) createTargets(caps)
        val programs = programs ?: buildPrograms().also { programs = it }
        if (debug.lens != LensMode.TABLE) programs.buildDebug()
        pendingTables?.let { tables ->
            pendingTables = null
            if (lens?.key != tables.key) {
                lens?.release()
                lens = LensTextures(tables)
            }
        }
        if (diskDetail == null) {
            diskDetail = DiskDetailTexture(
                programs.diskDetail, quality.diskDetailWidth, quality.diskDetailHeight, params.disk, maxAnisotropy(),
            )
        }
        if (haze == null) haze = HazeCubemap(programs.haze, quality.hazeFace, caps.hazeFormat, params.haze)
        // The timer runs while someone listens: its queries cost a little on every frame.
        if (onGpuTimeMs == null) {
            gpuTimer?.release()
            gpuTimer = null
        } else if (gpuTimer == null && caps.timerQuery) {
            gpuTimer = GpuTimer()
        }
    }

    private fun createTargets(caps: GlCaps) {
        val scale = quality.effectiveRenderScale(params.frames)
        val width = (surfaceWidth * scale).toInt().coerceAtLeast(1)
        val height = (surfaceHeight * scale).toInt().coerceAtLeast(1)
        for (candidate in caps.targetFormats) {
            val target = RenderTarget.create(width, height, candidate) ?: continue
            val previousFormat = format
            format = candidate
            scene = target
            if (previousFormat.isHdr != candidate.isHdr) {
                programs?.release()
                programs = null // HDR_TARGET is a shader define
            }
            bloom = BloomChain(width, height, quality.bloomLevels, candidate)
            return
        }
        error("No renderable colour format")
    }

    private fun createDebugTargets(scene: RenderTarget): Pair<RenderTarget, RenderTarget> =
        checkNotNull(RenderTarget.create(scene.width, scene.height, format)) to
            checkNotNull(RenderTarget.create(scene.width, scene.height, format))

    private fun buildPrograms(): Programs {
        val defines = mapOf(
            "K_MAX" to quality.diskCrossings,
            "STAR_LAYERS" to quality.starLayers,
            "PHI_MAX" to glslFloat(LensMath.PHI_MAX),
            "PHI_END_MAX" to glslFloat(LensMath.PHI_END_MAX),
            "HDR_TARGET" to if (format.isHdr) 1 else 0,
        ) + params.programDefines()
        val vertex = shaders.source("fullscreen.vert")
        fun program(name: String) = GlProgram(vertex, shaders.source(name, defines), name)
        return Programs(
            lens = program("lens.frag"),
            diskDetail = program("disk_detail.frag"),
            haze = program("haze.frag"),
            bloomDown = program("bloom_down.frag"),
            bloomUp = program("bloom_up.frag"),
            composite = program("composite.frag"),
            build = ::program,
        )
    }

    private fun maxAnisotropy(): Float {
        if ("GL_EXT_texture_filter_anisotropic" !in GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty()) return 1f
        val max = FloatArray(1)
        GLES30.glGetFloatv(DiskDetailTexture.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, max, 0)
        return max[0].coerceAtMost(MAX_ANISOTROPY)
    }

    /** A fixed random orientation per star layer, so the grids of the layers never line up (§9.1). */
    private fun starRotations(seed: Int): FloatArray {
        if (seed == starRotationSeed) return starRotations
        val layers = quality.starLayers
        starRotations = FloatArray(9 * layers)
        for (layer in 0 until layers) {
            val random = Random(seed * 31L + layer)
            // Shoemake's uniform random rotation.
            val u1 = random.nextDouble()
            val u2 = random.nextDouble() * 2 * PI
            val u3 = random.nextDouble() * 2 * PI
            val x = sqrt(1 - u1) * sin(u2)
            val y = sqrt(1 - u1) * cos(u2)
            val z = sqrt(u1) * sin(u3)
            val w = sqrt(u1) * cos(u3)
            val m = doubleArrayOf( // column-major
                1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w),
                2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w),
                2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y),
            )
            for (i in 0 until 9) starRotations[layer * 9 + i] = m[i].toFloat()
        }
        starRotationSeed = seed
        return starRotations
    }

    private fun releaseTargets() {
        scene?.release()
        scene = null
        debugTargets?.let { (a, b) -> a.release(); b.release() }
        debugTargets = null
        bloom?.release()
        bloom = null
    }

    private fun releaseGpu() {
        programs?.release()
        programs = null
        diskDetail?.release()
        diskDetail = null
        haze?.release()
        haze = null
        releaseTargets()
        gpuTimer?.release()
        gpuTimer = null
        starRotationSeed = Int.MIN_VALUE
    }

    /**
     * The wallpaper's programs, plus the debug screen's two built on first use: compiling the RK4 reference
     * makes the first frame much slower, and the wallpaper never draws it.
     */
    private class Programs(
        val lens: GlProgram,
        val diskDetail: GlProgram,
        val haze: GlProgram,
        val bloomDown: GlProgram,
        val bloomUp: GlProgram,
        val composite: GlProgram,
        private val build: (String) -> GlProgram,
    ) {
        private var referenceProgram: GlProgram? = null
        private var diffProgram: GlProgram? = null

        val reference: GlProgram get() = checkNotNull(referenceProgram) { "buildDebug() first" }
        val diff: GlProgram get() = checkNotNull(diffProgram) { "buildDebug() first" }

        /** Builds the debug programs if they aren't yet; throws [ShaderException] like the others. */
        fun buildDebug() {
            if (referenceProgram == null) referenceProgram = build("reference.frag")
            if (diffProgram == null) diffProgram = build("diff.frag")
        }

        fun release() = listOfNotNull(lens, diskDetail, haze, bloomDown, bloomUp, composite, referenceProgram, diffProgram)
            .forEach(GlProgram::release)
    }

    private val Tonemapper.index: Int get() = if (this == Tonemapper.ACES) 0 else 1

    /** The shader defines that come from the params: a change rebuilds the programs. */
    private fun BlackHoleParams.programDefines(): Map<String, Int> = mapOf(
        "OPTIMIZED" to if (frames.optimizedShaders) 1 else 0,
        "MAG_POWER_8" to if (stars.magPower == 8.0) 1 else 0,
    )

    private fun glslFloat(value: Double) = String.format(java.util.Locale.US, "%.8f", value)

    private companion object {
        /** The mean colour of the stars at warmth 0 and 1. */
        val STAR_TINT_COOL = floatArrayOf(0.93f, 0.835f, 0.78f)
        val STAR_TINT_WARM = floatArrayOf(1f, 0.96f, 0.86f)

        const val UNIT_TRAJECTORIES = 0
        const val UNIT_ENDS = 1
        const val UNIT_DISK = 2
        const val UNIT_HAZE = 3
        const val FP_MAX = 0.05f

        /** A fade past any angle a ray can have: the lens keeps its physical reach. */
        const val NO_FADE = 10f
        const val DIFF_SCALE = 10f
        const val LDR_THRESHOLD = 0.8f
        const val MAX_ANISOTROPY = 8f
        const val FADE_NANOS = 800_000_000L
        const val NOISE_FRAMES = 64
    }
}
