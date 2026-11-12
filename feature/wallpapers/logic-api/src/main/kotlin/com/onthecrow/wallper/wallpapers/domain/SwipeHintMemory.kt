package com.onthecrow.wallper.wallpapers.domain

/** The series editor's «swipe aside» hint shows on the first [SHOWS] visits of its members, then no more. */
interface SwipeHintMemory {

    /** Whether the hint is still to be shown. */
    suspend fun isDue(): Boolean

    /** Counts a visit that showed it. */
    fun countShown()

    companion object {
        const val SHOWS = 3
    }
}
