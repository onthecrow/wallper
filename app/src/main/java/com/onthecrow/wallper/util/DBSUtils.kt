package com.onthecrow.wallper.util

import android.content.Context
import java.io.File

object DBSUtils {

    /** Контекст, “привязанный” к device-protected storage (API 24+) */
    private fun dpsContext(context: Context): Context =
        context.createDeviceProtectedStorageContext()

    /** Полный путь к файлу в device-protected storage */
    private fun dpsFile(context: Context, fileName: String): File {
        val dps = dpsContext(context)
        val dir = dps.filesDir // гарантированно в DPS на API 24+
        return File(dir, fileName)
    }

    /** Сохранить байты в DPS */
    fun write(context: Context, fileName: String, bytes: ByteArray): File {
        val file = dpsFile(context, fileName)
        file.parentFile?.mkdirs()
        file.outputStream().use { it.write(bytes) }
        return file
    }
}