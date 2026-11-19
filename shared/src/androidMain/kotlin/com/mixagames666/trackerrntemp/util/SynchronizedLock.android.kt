package com.mixagames666.trackerrntemp.util

actual class SynchronizedLock actual constructor() {
    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor) { block() }
}
