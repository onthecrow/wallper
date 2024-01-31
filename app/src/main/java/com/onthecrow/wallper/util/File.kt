package com.onthecrow.wallper.util

import java.io.File

object FileUtils {
    fun deleteFileIfExists(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }
}