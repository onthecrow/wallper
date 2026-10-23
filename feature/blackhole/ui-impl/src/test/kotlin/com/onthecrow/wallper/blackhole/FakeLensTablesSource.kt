package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.blackhole.source.LensTablesSource
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Every load waits until the test completes or fails it. */
internal class FakeLensTablesSource : LensTablesSource {
    val requests = mutableListOf<LensTableKey>()
    private val pending = mutableMapOf<LensTableKey, CompletableDeferred<LensTables>>()
    private val cancelled = mutableSetOf<LensTableKey>()

    override suspend fun get(key: LensTableKey): LensTables {
        requests += key
        val result = CompletableDeferred<LensTables>().also { pending[key] = it }
        try {
            return result.await()
        } catch (e: CancellationException) {
            cancelled += key
            throw e
        }
    }

    fun complete(key: LensTableKey): LensTables =
        LensTables(LensTableKey(key.cameraDistance, 1, 1, key.k), FloatArray(2), FloatArray(2))
            .also { checkNotNull(pending.remove(key)).complete(it) }

    fun fail(key: LensTableKey, error: Throwable) {
        checkNotNull(pending.remove(key)).completeExceptionally(error)
    }

    fun isCancelled(key: LensTableKey) = key in cancelled
}
