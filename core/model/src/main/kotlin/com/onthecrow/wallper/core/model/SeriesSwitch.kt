package com.onthecrow.wallper.core.model

/**
 * How a series puts its next slide on screen. Stored by name in the settings: never rename one.
 * [SHARED_GL] is the way meant to stay; builds that aren't debuggable still get [CUT] for now.
 */
enum class SeriesSwitch {
    /** The slide's engine is released, then the next one is made from scratch. */
    CUT,

    /** Every slide draws on one GL thread and context; the next one is warmed beside the one on screen. */
    SHARED_GL,

    /**
     * Every slide keeps its own engine; the next one is warmed off the window and then takes it over.
     * Kept to compare with: it can only cut, where [SHARED_GL] could also blend two slides.
     */
    WARM_ENGINES,
}
