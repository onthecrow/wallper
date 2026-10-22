package com.onthecrow.wallper.core.gl.guard

/**
 * Watches the GL side: frames put on screen and EGL failures. Lives inside the GL thread's loop and is
 * driven by its events, so EGL errors are handled on the same frame. Repairs run one at a time; each
 * must be followed by a presented frame within its verify time, otherwise the next one runs. What
 * no repair fixes goes to [Listener]; the guard then stays quiet until frames come back.
 *
 * "No frames" is only nudged with redraws: the guard can't tell a lost frame callback from a player that
 * has nothing to show, and a redraw is harmless either way. Not thread-safe: GL thread only.
 */
class RenderGuard(
    private val repairs: RenderRepairs,
    private val listener: Listener,
    private val nowMs: () -> Long,
    private val log: (String) -> Unit = {},
) {
    fun interface Listener {
        /** GL can't be repaired in place; recreate the GL thread. Called on the GL thread. */
        fun onRenderUnrecoverable(stall: RenderStall)
    }

    private var expected = false
    private var lastFrameAtMs = nowMs()
    private var repair: Repair? = null
    private var gaveUpOn: RenderStall? = null

    /** Whether new frames are expected now; resets the guard on every change. */
    fun setExpected(value: Boolean) {
        if (expected == value) return
        expected = value
        repair = null
        gaveUpOn = null
        lastFrameAtMs = nowMs()
    }

    fun onFramePresented() {
        lastFrameAtMs = nowMs()
        gaveUpOn = null
        repair?.let { log("recovered from ${it.stall} by ${it.step.name}") }
        repair = null
    }

    fun onSwapFailed(eglError: Int) = report(
        if (eglError == EGL_BAD_SURFACE || eglError == EGL_BAD_NATIVE_WINDOW) RenderStall.SurfaceLost
        else RenderStall.ContextLost
    )

    fun onDrawFailed() = report(RenderStall.DrawFailed)

    /** Detects missing frames and moves to the next repair when the current one timed out. */
    fun check() {
        if (!expected || gaveUpOn != null) return
        val now = nowMs()
        val current = repair
        when {
            current != null -> if (now >= current.deadlineAtMs) next(current.stall, current.index + 1)
            now - lastFrameAtMs >= NO_FRAMES_TIMEOUT_MS -> next(RenderStall.NoFrames, 0)
        }
    }

    /** How long the GL loop may sleep before [check] is due. */
    fun nextCheckInMs(): Long {
        if (!expected || gaveUpOn != null) return Long.MAX_VALUE
        val dueAtMs = repair?.deadlineAtMs ?: (lastFrameAtMs + NO_FRAMES_TIMEOUT_MS)
        return (dueAtMs - nowMs()).coerceAtLeast(1)
    }

    private fun report(stall: RenderStall) {
        if (!expected) return
        val handled = repair?.stall ?: gaveUpOn
        // Repeated errors of the same kind don't restart the repairs; a worse one replaces them.
        if (handled != null && severity(stall) <= severity(handled)) return
        gaveUpOn = null
        next(stall, 0)
    }

    private fun next(stall: RenderStall, index: Int) {
        val step = stepsFor(stall).getOrNull(index)
        if (step == null) {
            repair = null
            gaveUpOn = stall
            log("unrecoverable $stall")
            if (stall != RenderStall.NoFrames) listener.onRenderUnrecoverable(stall)
            return
        }
        repair = Repair(stall, index, step, nowMs() + step.verifyMs)
        log("$stall: ${step.name}")
        step.action()
    }

    private fun stepsFor(stall: RenderStall): List<Step> = when (stall) {
        RenderStall.NoFrames -> listOf(redraw(300), redraw(1_000), redraw(1_000))
        RenderStall.SurfaceLost -> listOf(
            Step("recreate EGL surface", SURFACE_VERIFY_MS, repairs::recreateSurface),
            recreateContext(),
        )
        RenderStall.ContextLost, RenderStall.DrawFailed -> listOf(recreateContext())
    }

    private fun redraw(verifyMs: Long) = Step("redraw", verifyMs, repairs::redraw)

    private fun recreateContext() = Step("recreate EGL context", CONTEXT_VERIFY_MS, repairs::recreateContext)

    private fun severity(stall: RenderStall): Int = when (stall) {
        RenderStall.NoFrames -> 0
        RenderStall.SurfaceLost -> 1
        RenderStall.ContextLost, RenderStall.DrawFailed -> 2
    }

    private class Step(val name: String, val verifyMs: Long, val action: () -> Unit)

    private class Repair(val stall: RenderStall, val index: Int, val step: Step, val deadlineAtMs: Long)

    private companion object {
        const val NO_FRAMES_TIMEOUT_MS = 150L
        const val SURFACE_VERIFY_MS = 500L
        const val CONTEXT_VERIFY_MS = 1_500L

        // EGL14 values, kept here so the guard stays free of Android classes.
        const val EGL_BAD_NATIVE_WINDOW = 0x300B
        const val EGL_BAD_SURFACE = 0x300D
    }
}
