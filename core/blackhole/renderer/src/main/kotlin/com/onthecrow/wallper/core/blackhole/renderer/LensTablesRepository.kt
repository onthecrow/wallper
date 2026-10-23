package com.onthecrow.wallper.core.blackhole.renderer

import android.content.Context
import android.os.SystemClock
import com.onthecrow.wallper.core.blackhole.physics.LensTableFiles
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lens tables for the whole process (§11.3): shared by the wallpaper and its picker preview, the list
 * preview and the debug screen. Generated once per key and cached in device-protected storage, where
 * the direct-boot wallpaper can read them.
 *
 * Nothing stays in memory: a HIGH table is 8 MB, the callers drop it once it is on the GPU, and the file
 * reads back in tens of milliseconds when a new GL context needs it.
 */
@Singleton
class LensTablesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val files by lazy { LensTableFiles(File(context.createDeviceProtectedStorageContext().cacheDir, "blackhole")) }

    // One generation at a time: a second caller of the same key then finds the file.
    private val mutex = Mutex()

    suspend fun get(key: LensTableKey): LensTables = mutex.withLock {
        withContext(Dispatchers.IO) { read(key) } ?: generate(key)
    }

    private suspend fun generate(key: LensTableKey): LensTables {
        val start = SystemClock.elapsedRealtime()
        val tables = withContext(Dispatchers.Default) { LensTables.generate(key) }
        Timber.tag(TAG).i("Lens tables %s generated in %d ms", key, SystemClock.elapsedRealtime() - start)
        withContext(Dispatchers.IO) {
            runCatching { files.write(tables) }.onFailure { Timber.tag(TAG).w(it, "Can't cache the lens tables") }
        }
        return tables
    }

    private fun read(key: LensTableKey): LensTables? = runCatching { files.read(key) }
        .onFailure { Timber.tag(TAG).w(it, "Broken lens table cache, regenerating") }
        .getOrNull()

    private companion object {
        const val TAG = "BlackHole"
    }
}
