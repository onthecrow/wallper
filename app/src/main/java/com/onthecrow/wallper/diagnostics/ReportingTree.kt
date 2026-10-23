package com.onthecrow.wallper.diagnostics

import android.util.Log
import com.onthecrow.wallper.core.diagnostics.Diagnostics
import timber.log.Timber

/** Warnings become breadcrumbs and errors become reports, so the recoveries in the field aren't silent. */
internal class ReportingTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val line = if (tag != null) "$tag: $message" else message
        if (priority >= Log.ERROR) Diagnostics.reporter.recordNonFatal(line, t) else Diagnostics.reporter.log(line)
    }
}
