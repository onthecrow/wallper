package com.onthecrow.wallper.blackhole.physics

/**
 * The picture's fade from black: over [durationNanos] from the first frame that has the lens tables.
 *
 * A still frame (heat, power save) ends it for good: no frame follows it until something changes, so a
 * fade it began would stay black, which a new engine under severe heat showed for as long as it lasted.
 */
class FadeIn(private val durationNanos: Long = DURATION_NANOS) {

    private var startNanos = Long.MIN_VALUE

    /** How much of the picture a frame at [nowNanos] shows, eased from 0 to 1. */
    fun level(nowNanos: Long, still: Boolean): Double {
        if (startNanos == Long.MIN_VALUE) startNanos = nowNanos
        if (still) startNanos = minOf(startNanos, nowNanos - durationNanos)
        val t = ((nowNanos - startNanos) / durationNanos.toDouble()).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    companion object {
        const val DURATION_NANOS = 800_000_000L
    }
}
