package com.onthecrow.wallper.diagnostics

import android.content.Context
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Debug builds only: the log in a file in device-protected storage, so the wallpaper logs before the first
 * unlock too. Past [LIMIT_CHARS] it starts over and the full one becomes the previous log.
 */
internal class DebugFileTree(context: Context) : Timber.DebugTree() {

    private val directory = context.createDeviceProtectedStorageContext().filesDir
    private val file = File(directory, LOG_FILE)
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var size = 0L
    private var writer = open()

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // The message already ends with the stack trace of t.
        val line = "${formatter.format(System.currentTimeMillis())} ${LEVELS.getOrElse(priority - 2) { '?' }}/$tag: $message"
        writer.appendLine(line)
        writer.flush()
        size += line.length + 1
        if (size > LIMIT_CHARS) {
            writer.close()
            file.renameTo(File(directory, PREVIOUS_LOG_FILE))
            writer = open()
        }
    }

    private fun open(): BufferedWriter {
        size = file.length()
        return FileWriter(file, true).buffered()
    }

    companion object {
        private const val LOG_FILE = "wallper-log.txt"
        private const val PREVIOUS_LOG_FILE = "wallper-log.1.txt"
        private const val LIMIT_CHARS = 2_000_000L
        private const val LEVELS = "VDIWEA"

        /** Copies the logs to external app storage, where a file manager or `adb pull` reaches them. */
        fun copyToExternal(context: Context) {
            val target = context.getExternalFilesDir(null) ?: return
            val directory = context.createDeviceProtectedStorageContext().filesDir
            listOf(LOG_FILE, PREVIOUS_LOG_FILE).map { File(directory, it) }.filter(File::exists)
                .forEach { it.copyTo(File(target, it.name), overwrite = true) }
        }
    }
}
