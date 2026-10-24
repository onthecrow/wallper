package com.onthecrow.wallper.core.model

/** How a video wallpaper plays each time it is shown. Stored by name in the settings: never rename one. */
enum class VideoPlayback {
    /** Over and over, going on from where it stopped. */
    LOOP,

    /** Over and over, from the start each time it is shown. */
    RESTART,

    /** Once from the start each time it is shown, then it stays on its last frame. */
    ONCE,
}
