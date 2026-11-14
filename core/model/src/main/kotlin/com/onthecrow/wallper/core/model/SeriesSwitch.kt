package com.onthecrow.wallper.core.model

/**
 * How a series puts its next slide on screen. Stored by name in the settings: never rename one.
 * Only debuggable builds may pick another than [CUT] for now.
 */
enum class SeriesSwitch {
    /** The slide's engine is released, then the next one is made from scratch. */
    CUT,

    /** Every slide draws on one GL thread and context; the next one is warmed beside the one on screen. */
    SHARED_GL,

    /** Every slide keeps its own engine; the next one is warmed off the window and then takes it over. */
    WARM_ENGINES,
}
