package com.onthecrow.wallper.settings.domain

/**
 * Whether the installed app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`); faked in tests. Settings
 * that are only tried out, like [AppSettings.seriesSwitch], show and apply in such a build alone.
 */
fun interface DebugBuild {
    fun isDebuggable(): Boolean
}
