package com.onthecrow.wallper.core.diagnostics

/**
 * Where crashes and the problems the app recovers from are reported: a crash reporting service once one
 * is chosen. Until then the release build writes them to logcat.
 */
interface CrashReporter {
    /** Context attached to everything reported later, e.g. the GPU. */
    fun setKey(key: String, value: String)

    /** Something went wrong and the app went on: one report. */
    fun recordNonFatal(message: String, error: Throwable? = null)

    /** What happened before: kept with the next report. */
    fun log(message: String)
}

/**
 * The process-wide reporter, installed by the app at start. Global on purpose: GL threads and wallpaper
 * engines aren't built by the DI graph, and reports must work in direct boot, before anything else.
 */
object Diagnostics {
    @Volatile
    var reporter: CrashReporter = NoReporter

    private object NoReporter : CrashReporter {
        override fun setKey(key: String, value: String) = Unit
        override fun recordNonFatal(message: String, error: Throwable?) = Unit
        override fun log(message: String) = Unit
    }
}
