package com.onthecrow.wallper.blackhole.source

/**
 * Whether the installed app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`); faked in tests. A release
 * build only hides the debug tools with it: the debug screen and its shaders still ship.
 */
internal fun interface DebuggableBuild {
    fun isDebuggable(): Boolean
}
