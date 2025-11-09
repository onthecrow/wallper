package com.onthecrow.wallper

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import timber.log.Timber.Forest.plant
import timber.log.Timber.Tree
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern


@HiltAndroidApp
class WallperApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            plant(DebugTree())
            plant(FileTree())
        }
    }

    private inner class FileTree : Tree() {

        val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        val fileOS = this@WallperApplication.createDeviceProtectedStorageContext()
            .openFileOutput("wallper-log.txt", MODE_APPEND)
        val writer = fileOS.bufferedWriter()

        private val fqcnIgnore = listOf(
            Timber::class.java.name,
            Timber.Forest::class.java.name,
            Tree::class.java.name,
            DebugTree::class.java.name,
            FileTree::class.java.name,
        )

        val tag: String?
            get() = Throwable().stackTrace
                .first { it.className !in fqcnIgnore }
                .let(::createStackElementTag)

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            writer.appendLine("${formatter.format(System.currentTimeMillis())}: Priority: $priority, Tag: ${tag ?: this.tag}, Message: $message, Throwable:\n$t")
            writer.flush()
        }

        private fun createStackElementTag(element: StackTraceElement): String? {
            var tag = element.className.substringAfterLast('.')
            val m = ANONYMOUS_CLASS.matcher(tag)
            if (m.find()) {
                tag = m.replaceAll("")
            }
            // Tag length limit was removed in API 26.
            return if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
                tag
            } else {
                tag.substring(0, MAX_TAG_LENGTH)
            }
        }

        private val MAX_LOG_LENGTH = 4000
        private val MAX_TAG_LENGTH = 23
        private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
    }
}