package com.onthecrow.wallper.settings.domain

/**
 * Whether the installed app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`); faked in tests. Settings
 * that are only there to compare, like [AppSettings.seriesSwitch], show in such a build alone: another
 * build keeps their defaults.
 */
fun interface DebugBuild {
    fun isDebuggable(): Boolean
}
