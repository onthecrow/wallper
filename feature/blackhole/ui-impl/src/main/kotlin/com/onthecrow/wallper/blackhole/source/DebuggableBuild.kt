package com.onthecrow.wallper.blackhole.source

/**
 * Whether the installed app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`). Read at runtime rather
 * than from `BuildConfig`, so a release build stays free of the debug screen; faked in tests.
 */
internal fun interface DebuggableBuild {
    fun isDebuggable(): Boolean
}
