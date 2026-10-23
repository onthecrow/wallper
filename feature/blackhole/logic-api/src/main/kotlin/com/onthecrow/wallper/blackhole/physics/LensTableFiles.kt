package com.onthecrow.wallper.blackhole.physics

import java.io.File
import java.io.IOException

/**
 * The lens table cache in [directory]. Every quality of one (r_c, K, format) is kept: the wallpaper, its
 * list card and the system picker draw one each at the same time. Files of any other r_c, K or format are
 * stale once tables for a new one are written.
 */
class LensTableFiles(private val directory: File) {

    /** The cached tables for [key], or null if there are none; throws if the file is broken. */
    fun read(key: LensTableKey): LensTables? {
        val file = File(directory, key.fileName())
        if (!file.exists()) return null
        return file.inputStream().use { LensTables.readFrom(it, key) }
    }

    fun write(tables: LensTables) {
        directory.mkdirs()
        val group = tables.key.group()
        directory.listFiles { file -> file.name.startsWith(PREFIX) && !file.name.startsWith(group) }
            ?.forEach(File::delete)
        val target = File(directory, tables.key.fileName())
        val temp = File(directory, target.name + ".tmp")
        temp.outputStream().use(tables::writeTo)
        if (!temp.renameTo(target)) throw IOException("Can't move $temp to $target")
    }

    private fun LensTableKey.group() = "${PREFIX}v${LensTables.FORMAT_VERSION}_rc${cameraDistance}_k${k}_"

    private fun LensTableKey.fileName() = "${group()}a${nAlpha}_p$nPhi.bin"

    private companion object {
        const val PREFIX = "lens_"
    }
}
