package com.onthecrow.wallper.core.blackhole.renderer

import android.content.Context
import android.os.SystemClock
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The tables for [params] at [quality]. */
fun lensTableKey(params: BlackHoleParams, quality: QualityLevel) =
    LensTableKey(params.camera.distance, quality.tableAlpha, quality.tablePhi, params.lens.tableK)

/**
 * Lens tables for the whole process (§11.3): shared by the wallpaper and its picker preview, the list
 * preview and the debug screen. Generated once per key and cached in device-protected storage, where
 * the direct-boot wallpaper can read them.
 */
@Singleton
class LensTablesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val directory by lazy { File(context.createDeviceProtectedStorageContext().cacheDir, "blackhole") }
    private val mutex = Mutex()
    private val memory = LinkedHashMap<LensTableKey, LensTables>()

    suspend fun get(key: LensTableKey): LensTables = mutex.withLock {
        memory[key]?.let { return@withLock it }
        val tables = withContext(Dispatchers.IO) { read(key) } ?: generate(key)
        memory[key] = tables
        while (memory.size > MEMORY_ENTRIES) memory.remove(memory.keys.first())
        tables
    }

    private suspend fun generate(key: LensTableKey): LensTables {
        val start = SystemClock.elapsedRealtime()
        val tables = withContext(Dispatchers.Default) { LensTables.generate(key) }
        Timber.tag(TAG).i("Lens tables %s generated in %d ms", key, SystemClock.elapsedRealtime() - start)
        withContext(Dispatchers.IO) { write(tables) }
        return tables
    }

    private fun read(key: LensTableKey): LensTables? {
        val file = File(directory, key.fileName())
        if (!file.exists()) return null
        return runCatching { file.inputStream().use { LensTables.readFrom(it, key) } }
            .onFailure { Timber.tag(TAG).w(it, "Broken lens table cache, regenerating") }
            .getOrNull()
    }

    private fun write(tables: LensTables) {
        runCatching {
            directory.mkdirs()
            // Other keys are stale: r_c, the quality or the format changed.
            directory.listFiles()?.filter { it.name != tables.key.fileName() && it.name.startsWith("lens_") }
                ?.filter { name -> memory.keys.none { it.fileName() == name.name } }
                ?.forEach(File::delete)
            val temp = File(directory, tables.key.fileName() + ".tmp")
            temp.outputStream().use(tables::writeTo)
            temp.renameTo(File(directory, tables.key.fileName()))
        }.onFailure { Timber.tag(TAG).w(it, "Can't cache the lens tables") }
    }

    private companion object {
        const val TAG = "BlackHole"
        const val MEMORY_ENTRIES = 2
    }
}
