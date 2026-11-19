package com.mixagames666.trackerrntemp.util

import platform.Foundation.NSRecursiveLock

actual class SynchronizedLock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
