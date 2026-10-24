package com.onthecrow.wallper.core.gl

import android.os.Handler
import android.os.SystemClock
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.gl.guard.GlRestartPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A wallpaper engine's GL thread, replaced as a whole when it breaks or hangs: the restart, the heartbeat
 * and the teardown shared by every GL engine, the video one and the built-ins.
 *
 * The heartbeat and the restarts run on the engine's callback thread ([handler]); so do [restart] and [fail],
 * which post there. The heartbeat runs only while frames are expected: a hidden or still wallpaper never
 * wakes for it. [setVisible], [setFramesExpected], [onSurfaceChanged] and [onSurfaceDestroyed] may come
 * from any thread, and their latest values also reach a thread started later.
 *
 * A thread is replaced only after it has really exited. One stuck in the driver keeps its EGL surface on
 * the window, and a second one there fails; the engine gives up instead ([onGiveUp], with the reason).
 *
 * @param beforeRestart runs on [handler] before the old thread is stopped, e.g. to detach a video decoder.
 * @param afterRestart runs on [handler] once the new thread is started.
 * @param newSession builds a thread and its renderer, not started yet.
 */
class RestartableGl<S : RestartableGl.Session>(
    holder: SurfaceHolder,
    private val handler: Handler,
    private val onGiveUp: (reason: String, error: Throwable?) -> Unit,
    private val beforeRestart: () -> Unit = {},
    private val afterRestart: () -> Unit = {},
    private val newSession: () -> S,
) {
    /** One GL thread with whatever its renderer needs. */
    interface Session {
        val thread: WallpaperGLThread
    }

    private val restarts = GlRestartPolicy(SystemClock::elapsedRealtime)
    private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher())

    @Volatile
    private var session: S? = null

    @Volatile
    private var released = false

    @Volatile
    private var visible = true

    private val framesExpected = MutableStateFlow(false)

    @Volatile
    private var width = holder.surfaceFrame.width()

    @Volatile
    private var height = holder.surfaceFrame.height()

    /** When the current thread was first expected to draw, or null while it isn't. */
    @Volatile
    private var armedSinceMs: Long? = null

    /** The current thread's [WallpaperGLThread.presentedFrames] when slow work was last announced. */
    @Volatile
    private var slowFromFrame = 0L

    /** The running session; only valid after [start]. */
    val current: S get() = checkNotNull(session) { "RestartableGl not started" }

    /** Starts the first thread and the heartbeat. Separate from the constructor: sessions refer back to this. */
    fun start() {
        startSession(newSession())
        scope.launch { GlHeartbeat.run(framesExpected, ::checkHeartbeat) }
    }

    fun isCurrent(candidate: S): Boolean = session === candidate

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        val thread = session?.thread ?: return
        if (isVisible) thread.onResumeRendering() else thread.onPauseRendering()
    }

    /** Frames are expected now (visible, and something animates): arms the heartbeat. */
    fun setFramesExpected(expected: Boolean) {
        framesExpected.value = expected
        armedSinceMs = when {
            !expected -> null
            else -> armedSinceMs ?: SystemClock.elapsedRealtime()
        }
        session?.thread?.setFramesExpected(expected)
    }

    fun onSurfaceChanged(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        session?.thread?.onSurfaceCreatedOrChanged(newWidth, newHeight)
    }

    /** Blocks up to [waitMs] until the EGL surface is gone: the system may free the window right after. */
    fun onSurfaceDestroyed(waitMs: Long) {
        session?.thread?.onSurfaceDestroyed(waitMs)
    }

    /** The next frames compile shaders and may take seconds: the heartbeat waits longer for them. */
    fun expectSlowFrame() {
        slowFromFrame = session?.thread?.presentedFrames ?: 0
    }

    /** [candidate] can't be repaired in place: replace it, if it is still the current one. */
    fun restart(candidate: S, reason: String) {
        handler.post { if (isCurrent(candidate)) restartNow(reason) }
    }

    /** [candidate] can never draw here (no ES 3, shaders don't build): give up, if it is still the current one. */
    fun fail(candidate: S, error: Throwable) {
        handler.post {
            if (isCurrent(candidate) && !released) onGiveUp("GL can't render here", error)
        }
    }

    /**
     * Stops the heartbeat and the thread, on [handler] after anything already queued there, and waits up
     * to [timeoutMs] for that. A restart blocked meanwhile sees [released] and starts nothing.
     */
    fun release(timeoutMs: Long) {
        released = true
        val stopped = CountDownLatch(1)
        handler.post {
            scope.cancel()
            session?.thread?.requestExitAndWait()
            stopped.countDown()
        }
        if (!stopped.await(timeoutMs, TimeUnit.MILLISECONDS)) Timber.w("GL teardown still running after $timeoutMs ms")
    }

    /** A GL thread stuck inside a driver call can't notice that itself. */
    private fun checkHeartbeat() {
        val thread = session?.thread ?: return
        val hung = GlHeartbeat.isHung(
            nowMs = SystemClock.elapsedRealtime(),
            armedSinceMs = armedSinceMs,
            lastLoopAtMs = thread.lastLoopAtMs,
            slowFrame = thread.presentedFrames - slowFromFrame < GlHeartbeat.SLOW_FRAMES,
        )
        if (hung) restartNow("GL thread unresponsive")
    }

    private fun restartNow(reason: String) {
        if (released) return
        Timber.w("Restarting GL: $reason")
        if (!restarts.tryAcquire()) return giveUp("GL restart limit reached")
        beforeRestart()
        val old = current
        old.thread.requestExitAndWait()
        // release() may have come in while this blocked; it stops whatever is current, so start nothing.
        if (released) return
        if (old.thread.isAlive) return giveUp("The old GL thread is stuck in the driver and still holds the window")
        startSession(newSession())
        afterRestart()
    }

    private fun giveUp(reason: String) = onGiveUp(reason, null)

    private fun startSession(next: S) {
        session = next
        slowFromFrame = 0
        armedSinceMs = if (framesExpected.value) SystemClock.elapsedRealtime() else null
        // The latest state, read after the session changed: an update racing with this reaches one or the other.
        val thread = next.thread
        thread.onSurfaceCreatedOrChanged(width, height)
        if (!visible) thread.onPauseRendering()
        thread.setFramesExpected(framesExpected.value)
        thread.start()
    }
}

/** When a GL thread counts as hung, and when it is checked. Free of Android types, so it is tested on the JVM. */
object GlHeartbeat {
    const val CHECK_INTERVAL_MS = 500L
    const val TIMEOUT_MS = 2_000L

    /** The first frames of a thread, or after new shaders, compile them: seconds on a cold driver cache. */
    const val SLOW_FRAME_TIMEOUT_MS = 10_000L

    /** Frames that count as slow after the mark: a frame already in flight when it was set may come first. */
    const val SLOW_FRAMES = 2L

    /**
     * Calls [check] every [CHECK_INTERVAL_MS] while [framesExpected] is true, and never otherwise: nothing
     * can hang while no frames are expected, and a hidden wallpaper must not wake the CPU.
     */
    suspend fun run(framesExpected: Flow<Boolean>, check: () -> Unit) {
        framesExpected.collectLatest { expected ->
            if (!expected) return@collectLatest
            while (true) {
                delay(CHECK_INTERVAL_MS)
                check()
            }
        }
    }

    /**
     * @param armedSinceMs when frames became expected (or the thread was replaced); null while none are.
     * @param lastLoopAtMs the thread's last loop, [WallpaperGLThread.lastLoopAtMs].
     * @param slowFrame whether the thread may be compiling shaders right now.
     */
    fun isHung(nowMs: Long, armedSinceMs: Long?, lastLoopAtMs: Long, slowFrame: Boolean): Boolean {
        val since = armedSinceMs ?: return false
        val silentMs = nowMs - maxOf(lastLoopAtMs, since)
        return silentMs > if (slowFrame) SLOW_FRAME_TIMEOUT_MS else TIMEOUT_MS
    }
}
