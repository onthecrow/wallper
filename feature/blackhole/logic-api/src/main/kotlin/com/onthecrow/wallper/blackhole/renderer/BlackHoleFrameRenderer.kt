package com.onthecrow.wallper.blackhole.renderer

import com.onthecrow.wallper.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.blackhole.physics.FrameState
import com.onthecrow.wallper.blackhole.physics.LensTables
import com.onthecrow.wallper.blackhole.physics.QualityLevel

/**
 * The black hole renderer the wallpaper uses, for a live preview hosted elsewhere (a `GLSurfaceView`).
 * Every call on the thread whose GLES 3.0 context is current. GPU resources follow on the next [render].
 */
interface BlackHoleFrameRenderer {
    /** GPU time of a whole frame, when the driver can measure it. Null stops the measuring. */
    var onGpuTimeMs: ((Double) -> Unit)?

    /** Shaders failed to build on this device; [render] only clears. */
    val failure: Throwable?

    fun setParams(params: BlackHoleParams)

    /** Quality changes the shaders, the render scale and the baked textures; the tables come separately. */
    fun setQuality(level: QualityLevel)

    /** Held until the next frame uploads them, then dropped. */
    fun setTables(tables: LensTables)

    fun setDebug(options: DebugOptions)

    /** Size of the output; the lens pass runs at a fraction of it. */
    fun onSurfaceSize(width: Int, height: Int)

    /** The context is gone: its GPU objects are forgotten, not deleted. [setTables] again. */
    fun onContextLost()

    /**
     * Draws [frame] into [outputFramebuffer] (0 is the window). Returns false while the tables aren't
     * there yet or the shaders failed: nothing but a clear was drawn.
     */
    fun render(frame: FrameState, nowNanos: Long, outputFramebuffer: Int = 0): Boolean
}

/** A new renderer for each GL context owner; it reads its shaders from the app's assets. */
fun interface BlackHoleRendererFactory {
    fun create(): BlackHoleFrameRenderer
}

/** Debug-screen switches; the wallpaper always draws [LensMode.TABLE] and [DebugView.IMAGE]. */
data class DebugOptions(
    val lens: LensMode = LensMode.TABLE,
    val view: DebugView = DebugView.IMAGE,
)

enum class LensMode { TABLE, REFERENCE, SPLIT, DIFF }

enum class DebugView { IMAGE, TABLE_T, DISK_IMAGE, FOOTPRINT }
