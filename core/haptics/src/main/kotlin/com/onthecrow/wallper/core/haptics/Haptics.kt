package com.onthecrow.wallper.core.haptics

/** Short vibrations that confirm what the user's fingers did. Silent on a device without a vibrator. */
interface Haptics {
    /** A light tick: something clicked into place, like a picture snapping level. */
    fun tick()
}
