package com.onthecrow.wallper.blackhole.source

import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.physics.LensTables
import com.onthecrow.wallper.core.blackhole.renderer.LensTablesRepository
import javax.inject.Inject

/** Lens tables by key; [LensTablesRepository] in the app, a fake in tests. */
internal interface LensTablesSource {
    /** Generates the tables on a background dispatcher if they aren't cached; seconds at worst. */
    suspend fun get(key: LensTableKey): LensTables
}

internal class RepositoryLensTablesSource @Inject constructor(
    private val repository: LensTablesRepository,
) : LensTablesSource {
    override suspend fun get(key: LensTableKey): LensTables = repository.get(key)
}
