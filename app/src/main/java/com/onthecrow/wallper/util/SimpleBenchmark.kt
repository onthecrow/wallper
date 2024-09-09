package com.onthecrow.wallper.util

import timber.log.Timber

object SimpleBenchmark {

    private var lastUsedTimeStamp = System.currentTimeMillis()

    fun Any.benchPrintTimestamp(prefix: String) {
        Timber.d("[bench] - ${this.javaClass.simpleName} - $prefix - ${elapsedTime()}")
    }

    @Synchronized
    private fun elapsedTime(): Long {
        val elapsed = System.currentTimeMillis() - lastUsedTimeStamp
        lastUsedTimeStamp = System.currentTimeMillis()
        return elapsed
    }
}