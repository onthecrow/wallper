package com.onthecrow.wallper.core.model

/**
 * How a video wallpaper plays. A wake is a new look at the phone: the first time it plays after the screen
 * was off. Coming back to it from an app isn't one. Stored by name in the settings: never rename one.
 */
enum class VideoPlayback {
    /** Over and over, going on from where it stopped. */
    LOOP,

    /** Over and over, from the start at each wake. */
    RESTART,

    /** Once from the start at each wake, then it stays on its last frame. */
    ONCE,
}
