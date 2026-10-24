package com.onthecrow.wallper.settings

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.settings.domain.CreateReportUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.Writer
import java.util.Date
import javax.inject.Inject

/**
 * The report goes to the cache, where the share sheet's FileProvider finds it; it's made only from the
 * settings screen, after the first unlock. The log is the app's own: since Android 7 an app reads only
 * its own lines, the wallpaper service's included, as long as they are still in the log buffer.
 */
internal class CreateReportUseCaseImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchersProvider: DispatchersProvider,
) : CreateReportUseCase {

    override suspend fun invoke(): String = withContext(dispatchersProvider.io) {
        val dir = File(context.cacheDir, REPORTS_DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, REPORT_FILE)
        file.bufferedWriter().use { writer ->
            writeAbout(writer)
            writer.appendLine()
            writeLog(writer)
        }
        file.absolutePath
    }

    private fun writeAbout(writer: Writer) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        writer.appendLine("Wallper ${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})")
        writer.appendLine("${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
        writer.appendLine(Build.FINGERPRINT)
        writer.appendLine("Created ${Date()}")
    }

    private fun writeLog(writer: Writer) {
        runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOG_LINES.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { log -> log.copyTo(writer) }
            process.waitFor()
        }.onFailure { error ->
            Timber.e(error, "Can't read the log for a report")
            writer.appendLine("The log couldn't be read: $error")
        }
    }

    private companion object {
        /** Shared with the FileProvider's paths in ui-impl. */
        const val REPORTS_DIR = "reports"
        const val REPORT_FILE = "wallper-report.txt"
        const val LOG_LINES = 5_000
    }
}
