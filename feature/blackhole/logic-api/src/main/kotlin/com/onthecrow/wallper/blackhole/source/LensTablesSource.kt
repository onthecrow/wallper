package com.onthecrow.wallper.blackhole.source

import com.onthecrow.wallper.blackhole.physics.LensTableKey
import com.onthecrow.wallper.blackhole.physics.LensTables

/** Lens tables by key, shared by the wallpaper and the screens. */
interface LensTablesSource {
    /** Generates the tables on a background dispatcher if they aren't cached; seconds at worst. */
    suspend fun get(key: LensTableKey): LensTables
}
